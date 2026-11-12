package com.staybook.inventory.service;

import com.staybook.accommodation.dto.AccommodationDtos.RoomInfo;
import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.exception.BaseException;
import com.staybook.inventory.domain.InventoryHold;
import com.staybook.inventory.domain.InventoryHold.HoldStatus;
import com.staybook.inventory.domain.RoomInventory;
import com.staybook.inventory.dto.InventoryDtos.InventoryResponse;
import com.staybook.inventory.dto.InventoryDtos.NightPrice;
import com.staybook.inventory.dto.InventoryDtos.RoomAvailability;
import com.staybook.inventory.dto.InventoryDtos.SetInventoriesRequest;
import com.staybook.inventory.exception.InventoryErrorCode;
import com.staybook.inventory.repository.InventoryHoldRepository;
import com.staybook.inventory.repository.InventoryHoldRepository.HoldView;
import com.staybook.inventory.repository.RoomInventoryRepository;
import com.staybook.inventory.repository.RoomInventoryRepository.NightView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    /** 한 번에 조회·예약할 수 있는 최대 박수. */
    public static final int MAX_NIGHTS = 30;
    /** 호스트가 한 번에 설정·조회할 수 있는 최대 일수. */
    private static final int MAX_SET_DAYS = 366;

    private final RoomInventoryRepository inventoryRepository;
    private final InventoryHoldRepository holdRepository;
    private final AccommodationQueryService accommodationQueryService;
    private final InventoryProperties properties;
    private final Clock clock;

    // ---------- 다른 feature용: 선점·확정·해제 (모두 예약 ID 기준) ----------

    /**
     * 숙박일(checkIn 이상 checkOut 미만)마다 재고를 1개씩 선점한다.
     * 날짜 오름차순으로 조건부 UPDATE를 하고, 하루라도 실패하면 예외를 던져 호출한 트랜잭션 전체를 롤백시킨다.
     *
     * @return 날짜별 판매가 (날짜 오름차순)
     */
    @Transactional
    public List<NightPrice> hold(Long reservationId, Long roomId, LocalDate checkIn, LocalDate checkOut,
                                 LocalDateTime expiresAt) {
        long nights = validateStay(checkIn, checkOut);
        List<NightView> rows = inventoryRepository.findNights(roomId, checkIn, checkOut);
        if (rows.size() != nights) {
            // 재고 행이 없는 날짜가 있으면 판매하지 않는 날이다
            throw new BaseException(InventoryErrorCode.SOLD_OUT);
        }
        for (NightView row : rows) {
            if (inventoryRepository.tryHold(row.getId()) == 0) {
                throw new BaseException(InventoryErrorCode.SOLD_OUT);
            }
            holdRepository.save(InventoryHold.held(reservationId, row.getId(), expiresAt));
        }
        return rows.stream().map(r -> new NightPrice(r.getStayDate(), r.getPrice())).toList();
    }

    /**
     * 결제 완료: HELD인 선점을 CONFIRMED로 바꾸고 held_count - 1, booked_count + 1.
     * 이미 CONFIRMED거나 RELEASED인 행은 건드리지 않으므로 두 번 호출해도 결과가 같다.
     */
    @Transactional
    public void confirm(Long reservationId) {
        for (HoldView hold : holdRepository.findByReservation(reservationId)) {
            if (holdRepository.transition(hold.getId(), HoldStatus.HELD, HoldStatus.CONFIRMED) == 1) {
                inventoryRepository.moveHeldToBooked(hold.getRoomInventoryId());
            }
        }
    }

    /**
     * 만료·취소: hold 상태에 따라 해당 카운트를 되돌리고 RELEASED로 바꾼다.
     * HELD였으면 held_count - 1, CONFIRMED였으면 booked_count - 1. 두 번 호출해도 결과가 같다.
     */
    @Transactional
    public void release(Long reservationId) {
        for (HoldView hold : holdRepository.findByReservation(reservationId)) {
            if (holdRepository.transition(hold.getId(), HoldStatus.HELD, HoldStatus.RELEASED) == 1) {
                inventoryRepository.decreaseHeld(hold.getRoomInventoryId());
            } else if (holdRepository.transition(hold.getId(), HoldStatus.CONFIRMED, HoldStatus.RELEASED) == 1) {
                inventoryRepository.decreaseBooked(hold.getRoomInventoryId());
            }
        }
    }

    // ---------- 공개 조회 ----------

    /** 숙소의 객실별 예약 가능 여부와 기간 합계 요금. */
    @Transactional(readOnly = true)
    public List<RoomAvailability> getAvailability(Long accommodationId, LocalDate checkIn, LocalDate checkOut) {
        int nights = (int) validateStay(checkIn, checkOut);
        if (checkIn.isBefore(LocalDate.now(clock))) {
            throw new BaseException(InventoryErrorCode.INVALID_DATE_RANGE);
        }
        List<RoomInfo> rooms = accommodationQueryService.getSellableRooms(accommodationId);
        if (rooms.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RoomInventory>> byRoom = inventoryRepository
                .findForStay(rooms.stream().map(RoomInfo::roomId).toList(), checkIn, checkOut).stream()
                .collect(Collectors.groupingBy(RoomInventory::getRoomId));

        return rooms.stream().map(room -> {
            List<RoomInventory> rows = byRoom.getOrDefault(room.roomId(), List.of());
            boolean complete = rows.size() == nights;
            boolean available = complete && rows.stream().allMatch(RoomInventory::isAvailable);
            BigDecimal totalPrice = complete
                    ? rows.stream().map(RoomInventory::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;
            return new RoomAvailability(room.roomId(), room.roomName(), room.capacity(), room.maxCapacity(),
                    room.extraPersonFee(), nights, available, totalPrice);
        }).toList();
    }

    // ---------- 호스트 ----------

    /**
     * 기간별 재고 조회 (startDate부터 endDate까지, 양 끝 포함). 본인 소유의 객실인지 먼저 검사한다.
     * 지난 날짜도 조회할 수 있다. 재고 행이 없는 날짜는 결과에 나오지 않는다(판매하지 않는 날).
     */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventories(Long userId, Long roomId, LocalDate start, LocalDate end) {
        accommodationQueryService.assertRoomOwner(roomId, userId);
        if (end.isBefore(start)) {
            throw new BaseException(InventoryErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_SET_DAYS) {
            throw new BaseException(InventoryErrorCode.DATE_RANGE_TOO_LONG);
        }
        return inventoryRepository.findByRoomIdAndStayDateBetweenOrderByStayDateAsc(roomId, start, end).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    /** 기간별 수량, 가격, 판매 마감 설정. 본인 소유의 객실인지 먼저 검사한다. */
    @Transactional
    public List<InventoryResponse> setInventories(Long userId, Long roomId, SetInventoriesRequest request) {
        accommodationQueryService.assertRoomOwner(roomId, userId);
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start) || start.isBefore(LocalDate.now(clock))) {
            throw new BaseException(InventoryErrorCode.INVALID_DATE_RANGE);
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_SET_DAYS) {
            throw new BaseException(InventoryErrorCode.DATE_RANGE_TOO_LONG);
        }
        if (request.totalCount() == null && request.price() == null && request.closed() == null) {
            throw new BaseException(InventoryErrorCode.NOTHING_TO_UPDATE);
        }

        RoomInfo room = accommodationQueryService.getRoomInfo(roomId);
        int newRowCount = request.totalCount() != null ? request.totalCount() : baseTotalCount(roomId);
        BigDecimal newRowPrice = request.price() != null ? request.price() : room.nightlyPrice();

        // 없는 날짜는 먼저 만들고(동시에 만들어져도 무시), 그 다음 전체를 읽어 요청 값으로 바꾼다
        Set<LocalDate> existing = new HashSet<>(inventoryRepository.findDates(roomId, start, end));
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!existing.contains(date)) {
                inventoryRepository.insertIfAbsent(roomId, date, newRowCount, newRowPrice);
            }
        }
        List<RoomInventory> rows = inventoryRepository.findByRoomIdAndStayDateBetweenOrderByStayDateAsc(roomId, start, end);
        for (RoomInventory row : rows) {
            if (request.totalCount() != null && request.totalCount() < row.getBookedCount() + row.getHeldCount()) {
                throw new BaseException(InventoryErrorCode.TOTAL_COUNT_TOO_SMALL);
            }
            row.change(request.totalCount(), request.price(), request.closed());
        }
        return rows.stream().map(InventoryResponse::from).toList();
    }

    // ---------- 스케줄러 ----------

    /**
     * 판매 중인 모든 객실에 대해 오늘부터 N일 뒤까지 없는 날짜의 재고 행을 만든다.
     * 가격은 객실 기본가(nightly_price)를 쓴다. 이미 있는 행은 건드리지 않는다.
     *
     * @return 새로 만든 행 수
     */
    @Transactional
    public int generateUpcoming() {
        LocalDate today = LocalDate.now(clock);
        LocalDate last = today.plusDays(properties.generateDays());
        int created = 0;
        for (RoomInfo room : accommodationQueryService.getAllActiveRooms()) {
            Set<LocalDate> existing = new HashSet<>(inventoryRepository.findDates(room.roomId(), today, last));
            if (existing.size() > properties.generateDays()) {
                continue;
            }
            int totalCount = baseTotalCount(room.roomId());
            for (LocalDate date = today; !date.isAfter(last); date = date.plusDays(1)) {
                if (!existing.contains(date)) {
                    created += inventoryRepository.insertIfAbsent(room.roomId(), date, totalCount, room.nightlyPrice());
                }
            }
        }
        return created;
    }

    // ---------- 내부 ----------

    /** 그 객실의 가장 마지막 날짜 재고의 수량을 이어받는다. 재고가 하나도 없으면 설정값을 쓴다. */
    private int baseTotalCount(Long roomId) {
        return inventoryRepository.findFirstByRoomIdOrderByStayDateDesc(roomId)
                .map(RoomInventory::getTotalCount)
                .orElse(properties.defaultTotalCount());
    }

    /** 1박 이상, 최대 박수 이하인지 검사하고 박수를 돌려준다. */
    private long validateStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BaseException(InventoryErrorCode.INVALID_DATE_RANGE);
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new BaseException(InventoryErrorCode.DATE_RANGE_TOO_LONG);
        }
        return nights;
    }
}

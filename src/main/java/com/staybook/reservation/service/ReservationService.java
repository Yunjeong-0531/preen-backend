package com.staybook.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomInfo;
import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.response.PageResponse;
import com.staybook.inventory.dto.InventoryDtos.NightPrice;
import com.staybook.inventory.service.InventoryService;
import com.staybook.reservation.domain.Reservation;
import com.staybook.reservation.domain.Reservation.Status;
import com.staybook.reservation.domain.ReservationPriceItem;
import com.staybook.reservation.domain.ReservationPriceItem.ItemType;
import com.staybook.reservation.domain.ReservationStatusHistory;
import com.staybook.reservation.dto.ReservationDtos.CancelResponse;
import com.staybook.reservation.dto.ReservationDtos.CreateRequest;
import com.staybook.reservation.dto.ReservationDtos.PaymentInfo;
import com.staybook.reservation.dto.ReservationDtos.PriceItemResponse;
import com.staybook.reservation.dto.ReservationDtos.ReservationResponse;
import com.staybook.reservation.dto.ReservationDtos.StayInfo;
import com.staybook.reservation.exception.ReservationErrorCode;
import com.staybook.reservation.repository.ReservationPriceItemRepository;
import com.staybook.reservation.repository.ReservationRepository;
import com.staybook.reservation.repository.ReservationStatusHistoryRepository;
import com.staybook.user.dto.UserMeResponse;
import com.staybook.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예약 생성·조회·취소와 상태 전이.
 *
 * <pre>
 * PENDING   → CONFIRMED  결제 성공 (재고 confirm)
 * PENDING   → CANCELLED  선점 만료 또는 결제 전 취소 (재고 release)
 * CONFIRMED → CANCELLED  사용자 취소 (재고 release, 환불 이벤트 발행)
 * CONFIRMED → COMPLETED  체크아웃 날짜 경과
 * </pre>
 * 상태를 바꿀 때는 먼저 예약 행을 잠그고(FOR UPDATE) 현재 상태를 확인하며, 바뀔 때마다 이력을 남긴다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String NO_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int NO_RANDOM_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReservationRepository reservationRepository;
    private final ReservationPriceItemRepository priceItemRepository;
    private final ReservationStatusHistoryRepository historyRepository;
    private final UserService userService;
    private final AccommodationQueryService accommodationQueryService;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ReservationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ---------- 생성 ----------

    /**
     * 예약 생성. 한 트랜잭션으로 예약 저장(PENDING), 재고 선점, 요금 항목 저장을 한다.
     * 재고 선점이 하루라도 실패하면 전체가 롤백된다.
     */
    @Transactional
    public ReservationResponse create(Long userId, CreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (!request.checkOut().isAfter(request.checkIn()) || request.checkIn().isBefore(today)) {
            throw new BaseException(ReservationErrorCode.INVALID_DATES);
        }
        if (!userService.isAdult(userId)) {
            throw new BaseException(ReservationErrorCode.ADULT_ONLY);
        }
        RoomInfo room = accommodationQueryService.getRoomInfo(request.roomId());
        if (!room.sellable()) {
            throw new BaseException(ReservationErrorCode.ROOM_NOT_AVAILABLE);
        }
        if (request.guestCount() > room.maxCapacity()) {
            throw new BaseException(ReservationErrorCode.GUEST_COUNT_EXCEEDED);
        }
        RefundPolicyResponse policy = accommodationQueryService.getActiveRefundPolicy(room.accommodationId())
                .orElseThrow(() -> new BaseException(ReservationErrorCode.ROOM_NOT_AVAILABLE));

        UserMeResponse me = userService.getMe(userId);
        String guestName = request.guestName() != null ? request.guestName().trim() : me.name();
        String guestPhone = request.guestPhone() != null ? request.guestPhone() : me.phone();
        LocalDateTime holdExpiresAt = now.plus(properties.holdDuration());

        Reservation reservation = reservationRepository.save(Reservation.pending(
                newReservationNo(today), userId, room.roomId(), policy.id(), request.checkIn(), request.checkOut(),
                request.guestCount(), guestName, guestPhone, toJson(policy), now, holdExpiresAt));

        List<NightPrice> nights = inventoryService.hold(reservation.getId(), room.roomId(),
                request.checkIn(), request.checkOut(), holdExpiresAt);

        List<ReservationPriceItem> items = new ArrayList<>();
        BigDecimal roomAmount = nights.stream().map(NightPrice::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        items.add(ReservationPriceItem.of(reservation.getId(), ItemType.ROOM,
                "객실 요금 (" + nights.size() + "박)", roomAmount));
        int extraPersons = request.guestCount() - room.capacity();
        BigDecimal extraAmount = room.extraPersonFee()
                .multiply(BigDecimal.valueOf(extraPersons))
                .multiply(BigDecimal.valueOf(nights.size()));
        if (extraAmount.signum() > 0) {
            items.add(ReservationPriceItem.of(reservation.getId(), ItemType.EXTRA_PERSON,
                    "추가 인원 요금 (" + extraPersons + "명 × " + nights.size() + "박)", extraAmount));
        }
        priceItemRepository.saveAll(items);
        reservation.changeTotalPrice(items.stream().map(ReservationPriceItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        recordHistory(reservation.getId(), userId, null, Status.PENDING, "예약 생성", now);
        return toResponse(reservation, items, room);
    }

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getMine(Long userId, int page, int size) {
        return toPage(reservationRepository.findByUserId(userId, pageable(page, size)));
    }

    /** 본인 예약만 볼 수 있다. 남의 예약은 없는 예약으로 응답한다. */
    @Transactional(readOnly = true)
    public ReservationResponse getDetail(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        return toResponse(reservation);
    }

    /** 호스트 본인 숙소의 예약 목록. */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getForHost(Long hostUserId, int page, int size) {
        List<Long> roomIds = accommodationQueryService.getRoomIdsOfHost(hostUserId);
        if (roomIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable(page, size)));
        }
        return toPage(reservationRepository.findByRoomIdIn(roomIds, pageable(page, size)));
    }

    // ---------- 취소 ----------

    /**
     * 예약 취소. 결제 전(PENDING)이면 재고만 되돌린다.
     * 결제 후(CONFIRMED)면 재고를 되돌리고, 스냅샷 규칙으로 구한 환불 비율을 이벤트에 담아 발행한다.
     * 실제 환불은 payment가 커밋 이후에 처리한다.
     */
    @Transactional
    public CancelResponse cancel(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);

        if (reservation.isStatus(Status.PENDING)) {
            transitionToCancelled(reservation, userId, "결제 전 취소", now);
            return new CancelResponse(toResponse(reservation), null, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (reservation.isStatus(Status.CONFIRMED) && !now.toLocalDate().isAfter(reservation.getCheckIn())) {
            long daysBeforeCheckIn = ChronoUnit.DAYS.between(now.toLocalDate(), reservation.getCheckIn());
            BigDecimal refundRate = refundRate(reservation.getRefundPolicySnapshot(), daysBeforeCheckIn);
            transitionToCancelled(reservation, userId, "사용자 취소", now);
            eventPublisher.publishEvent(new ReservationCancelledEvent(reservationId, refundRate, "사용자 취소"));

            BigDecimal refundAmount = reservation.getTotalPrice().multiply(refundRate).setScale(0, RoundingMode.DOWN);
            return new CancelResponse(toResponse(reservation), refundRate, refundAmount,
                    reservation.getTotalPrice().subtract(refundAmount));
        }
        throw new BaseException(ReservationErrorCode.NOT_CANCELLABLE);
    }

    // ---------- payment용 ----------

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentInfo(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        RoomInfo room = accommodationQueryService.getRoomInfo(reservation.getRoomId());
        return new PaymentInfo(reservation.getId(), reservation.getReservationNo(), reservation.getUserId(),
                reservation.getTotalPrice(), reservation.getStatus().name(), reservation.isStatus(Status.PENDING),
                room.accommodationName() + " " + room.roomName());
    }

    /**
     * 결제 성공 시 payment가 호출한다. PENDING이면 CONFIRMED로 바꾸고 재고를 확정한다.
     *
     * @return 예약이 확정 상태면 true(이미 확정된 경우 포함). 이미 취소됐거나 끝난 예약이면 false
     */
    @Transactional
    public boolean confirm(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        if (reservation.isStatus(Status.CONFIRMED)) {
            return true;
        }
        if (!reservation.isStatus(Status.PENDING)) {
            return false;
        }
        reservation.confirm();
        inventoryService.confirm(reservationId);
        recordHistory(reservationId, null, Status.PENDING, Status.CONFIRMED, "결제 완료", LocalDateTime.now(clock));
        return true;
    }

    // ---------- review용 ----------

    @Transactional(readOnly = true)
    public StayInfo getStayInfo(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        RoomInfo room = accommodationQueryService.getRoomInfo(reservation.getRoomId());
        return new StayInfo(reservation.getId(), reservation.getUserId(), room.accommodationId(),
                reservation.getStatus().name(), reservation.isStatus(Status.COMPLETED));
    }

    // ---------- 스케줄러 ----------

    /**
     * 결제 제한 시간이 지난 PENDING 예약을 취소하고 재고를 되돌린다.
     * 예약마다 따로 커밋해서 한 건의 실패가 다른 건을 막지 않게 한다.
     *
     * @return 취소한 예약 수
     */
    public int expirePending() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = 0;
        for (Long id : reservationRepository.findIdsByStatusAndHoldExpired(Status.PENDING, now)) {
            Boolean done = transactionTemplate.execute(tx -> reservationRepository.findByIdForUpdate(id)
                    // 그 사이 결제가 끝났을 수 있으므로 잠근 뒤 다시 확인한다
                    .filter(r -> r.isStatus(Status.PENDING) && !r.getHoldExpiresAt().isAfter(now))
                    .map(r -> {
                        transitionToCancelled(r, null, "결제 시간 만료", now);
                        return true;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(done)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * 체크아웃 날짜가 지난 CONFIRMED 예약을 COMPLETED로 바꾼다.
     *
     * @return 완료 처리한 예약 수
     */
    public int completeStays() {
        LocalDateTime now = LocalDateTime.now(clock);
        int completed = 0;
        for (Long id : reservationRepository.findIdsByStatusAndCheckOutBefore(Status.CONFIRMED, now.toLocalDate())) {
            Boolean done = transactionTemplate.execute(tx -> reservationRepository.findByIdForUpdate(id)
                    .filter(r -> r.isStatus(Status.CONFIRMED))
                    .map(r -> {
                        r.complete();
                        recordHistory(id, null, Status.CONFIRMED, Status.COMPLETED, "체크아웃 경과", now);
                        return true;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(done)) {
                completed++;
            }
        }
        return completed;
    }

    // ---------- 내부 ----------

    private void transitionToCancelled(Reservation reservation, Long changedBy, String reason, LocalDateTime now) {
        Status previous = reservation.getStatus();
        reservation.cancel(now);
        inventoryService.release(reservation.getId());
        recordHistory(reservation.getId(), changedBy, previous, Status.CANCELLED, reason, now);
    }

    private void recordHistory(Long reservationId, Long changedBy, Status previous, Status next, String reason,
                               LocalDateTime now) {
        historyRepository.save(ReservationStatusHistory.of(reservationId, changedBy, previous, next, reason, now));
    }

    /**
     * 스냅샷 규칙에서 체크인까지 남은 일수에 맞는 환불 비율을 찾는다.
     * "체크인 N일 전까지" 규칙 중 남은 일수가 N 이상인 것에서 N이 가장 큰 규칙을 적용하고, 맞는 규칙이 없으면 0이다.
     */
    static BigDecimal refundRate(String snapshotJson, long daysBeforeCheckIn, ObjectMapper objectMapper) {
        try {
            int bestDays = -1;
            BigDecimal rate = BigDecimal.ZERO;
            for (JsonNode rule : objectMapper.readTree(snapshotJson).path("rules")) {
                int days = rule.path("daysBefore").asInt();
                if (days <= daysBeforeCheckIn && days > bestDays) {
                    bestDays = days;
                    rate = rule.path("refundRate").decimalValue();
                }
            }
            return rate;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("환불정책 스냅샷을 읽을 수 없습니다.", e);
        }
    }

    private BigDecimal refundRate(String snapshotJson, long daysBeforeCheckIn) {
        return refundRate(snapshotJson, daysBeforeCheckIn, objectMapper);
    }

    /** 추측하기 어려운 예약번호. 예: R261111-K7M2Q9XWPB4T */
    private static String newReservationNo(LocalDate today) {
        StringBuilder sb = new StringBuilder("R").append(today.format(DateTimeFormatter.ofPattern("yyMMdd")))
                .append('-');
        for (int i = 0; i < NO_RANDOM_LENGTH; i++) {
            sb.append(NO_ALPHABET.charAt(RANDOM.nextInt(NO_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
    }

    private PageResponse<ReservationResponse> toPage(Page<Reservation> page) {
        List<Long> ids = page.getContent().stream().map(Reservation::getId).toList();
        Map<Long, List<ReservationPriceItem>> items = ids.isEmpty() ? Map.of()
                : priceItemRepository.findByReservationIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(ReservationPriceItem::getReservationId));
        return PageResponse.from(page, r -> toResponse(r, items.getOrDefault(r.getId(), List.of()),
                accommodationQueryService.getRoomInfo(r.getRoomId())));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return toResponse(reservation,
                priceItemRepository.findByReservationIdInOrderByIdAsc(List.of(reservation.getId())),
                accommodationQueryService.getRoomInfo(reservation.getRoomId()));
    }

    private ReservationResponse toResponse(Reservation r, List<ReservationPriceItem> items, RoomInfo room) {
        JsonNode policy;
        try {
            policy = objectMapper.readTree(r.getRefundPolicySnapshot());
        } catch (JsonProcessingException e) {
            policy = null;
        }
        return new ReservationResponse(r.getId(), r.getReservationNo(), room.accommodationId(),
                room.accommodationName(), room.roomId(), room.roomName(), r.getCheckIn(), r.getCheckOut(),
                r.getGuestCount(), r.getGuestName(), r.getGuestPhone(), r.getTotalPrice(), r.getStatus().name(),
                r.getHoldExpiresAt(), r.getCancelledAt(), r.getCreatedAt(),
                items.stream().map(i -> new PriceItemResponse(i.getItemType().name(), i.getName(), i.getAmount()))
                        .toList(),
                policy);
    }
}

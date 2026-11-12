package com.staybook.accommodation.service;

import com.staybook.accommodation.domain.Accommodation;
import com.staybook.accommodation.domain.AccommodationAmenity;
import com.staybook.accommodation.domain.AccommodationCategory;
import com.staybook.accommodation.domain.AccommodationImage;
import com.staybook.accommodation.domain.Amenity;
import com.staybook.accommodation.domain.RefundPolicy;
import com.staybook.accommodation.domain.Region;
import com.staybook.accommodation.domain.Room;
import com.staybook.accommodation.domain.RoomImage;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationDetail;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationSummary;
import com.staybook.accommodation.dto.AccommodationDtos.AmenityResponse;
import com.staybook.accommodation.dto.AccommodationDtos.CategoryResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RefundRule;
import com.staybook.accommodation.dto.AccommodationDtos.RegionResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomInfo;
import com.staybook.accommodation.dto.AccommodationDtos.RoomResponse;
import com.staybook.accommodation.exception.AccommodationErrorCode;
import com.staybook.accommodation.repository.AccommodationAmenityRepository;
import com.staybook.accommodation.repository.AccommodationCategoryRepository;
import com.staybook.accommodation.repository.AccommodationImageRepository;
import com.staybook.accommodation.repository.AccommodationRepository;
import com.staybook.accommodation.repository.AmenityRepository;
import com.staybook.accommodation.repository.RefundPolicyRepository;
import com.staybook.accommodation.repository.RefundPolicyRuleRepository;
import com.staybook.accommodation.repository.RegionRepository;
import com.staybook.accommodation.repository.RoomImageRepository;
import com.staybook.accommodation.repository.RoomRepository;
import com.staybook.common.exception.BaseException;
import com.staybook.common.response.PageResponse;
import com.staybook.host.service.HostService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 조회 전용. 공개 API와, 다른 feature(재고·예약·리뷰·찜)가 쓰는 조회 메서드를 둔다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccommodationQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AccommodationRepository accommodationRepository;
    private final AccommodationImageRepository imageRepository;
    private final AccommodationAmenityRepository accommodationAmenityRepository;
    private final AmenityRepository amenityRepository;
    private final RegionRepository regionRepository;
    private final AccommodationCategoryRepository categoryRepository;
    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final RefundPolicyRuleRepository refundPolicyRuleRepository;
    private final HostService hostService;

    // ---------- 공개 조회 ----------

    public List<RegionResponse> getRegions() {
        return regionRepository.findAll(Sort.by("id")).stream()
                .map(r -> new RegionResponse(r.getId(), r.getParentId(), r.getName(), r.getLevel()))
                .toList();
    }

    public List<AmenityResponse> getAmenities() {
        return amenityRepository.findAll(Sort.by("id")).stream()
                .map(a -> new AmenityResponse(a.getId(), a.getName()))
                .toList();
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .toList();
    }

    /**
     * 목록 검색. LISTED인 숙소만, 최근 등록순.
     * 시·도를 고르면 그 아래 시·군·구의 숙소도 함께 나온다. 카테고리는 정확히 그 유형인 숙소만 나온다.
     */
    public PageResponse<AccommodationSummary> search(Long regionId, Long categoryId, Integer guestCount, int page, int size) {
        List<Long> regionIds = new ArrayList<>();
        if (regionId != null) {
            regionIds.add(regionId);
            regionRepository.findByParentId(regionId).forEach(child -> regionIds.add(child.getId()));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        // 공개 조회에는 LISTED인 숙소만 나온다
        Page<Accommodation> result = accommodationRepository.search(
                Accommodation.ListingStatus.LISTED, Room.RoomStatus.ACTIVE, regionId != null,
                regionId != null ? regionIds : List.of(-1L), categoryId, guestCount, pageable);

        Map<Long, AccommodationSummary> summaries = toSummaries(result.getContent());
        return PageResponse.from(result, a -> summaries.get(a.getId()));
    }

    /** 공개(LISTED)된 숙소의 목록용 요약. 찜 목록처럼 ID로 여러 숙소를 보여 줄 때 쓴다. 공개되지 않은 숙소는 빠진다. */
    public Map<Long, AccommodationSummary> getListedSummaries(java.util.Collection<Long> accommodationIds) {
        if (accommodationIds.isEmpty()) {
            return Map.of();
        }
        return toSummaries(accommodationRepository.findAllById(accommodationIds).stream()
                .filter(Accommodation::isListed).toList());
    }

    private Map<Long, AccommodationSummary> toSummaries(List<Accommodation> accommodations) {
        List<Long> ids = accommodations.stream().map(Accommodation::getId).toList();
        Map<Long, String> thumbnails = new HashMap<>();
        Map<Long, BigDecimal> minPrices = new HashMap<>();
        Map<Long, String> regionNames = new HashMap<>();
        Map<Long, String> categoryNames = new HashMap<>();
        if (!ids.isEmpty()) {
            // 정렬 순서대로 오므로 숙소별 첫 이미지가 대표 이미지다
            imageRepository.findByAccommodationIdInOrderBySortOrderAsc(ids)
                    .forEach(i -> thumbnails.putIfAbsent(i.getAccommodationId(), i.getImageUrl()));
            roomRepository.findByAccommodationIdIn(ids).stream()
                    .filter(Room::isActive)
                    .forEach(r -> minPrices.merge(r.getAccommodationId(), r.getNightlyPrice(), BigDecimal::min));
            regionRepository.findAllById(accommodations.stream().map(Accommodation::getRegionId).toList())
                    .forEach(r -> regionNames.put(r.getId(), r.getName()));
            categoryRepository.findAllById(accommodations.stream().map(Accommodation::getCategoryId).toList())
                    .forEach(c -> categoryNames.put(c.getId(), c.getName()));
        }
        Map<Long, AccommodationSummary> summaries = new HashMap<>();
        accommodations.forEach(a -> summaries.put(a.getId(), new AccommodationSummary(a.getId(), a.getName(),
                a.getAddress(), a.getRegionId(), regionNames.get(a.getRegionId()),
                a.getCategoryId(), categoryNames.get(a.getCategoryId()), thumbnails.get(a.getId()),
                minPrices.get(a.getId()))));
        return summaries;
    }

    /** 공개 상세. LISTED가 아니면 없는 숙소로 응답한다. 판매 중인 객실만 보여 준다. */
    public AccommodationDetail getListedDetail(Long accommodationId) {
        Accommodation accommodation = accommodationRepository.findById(accommodationId)
                .filter(Accommodation::isListed)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ACCOMMODATION_NOT_FOUND));
        return toDetail(accommodation, true);
    }

    /** 상세 조립. 호스트 응답에서는 판매 중지 객실도 포함한다. */
    public AccommodationDetail toDetail(Accommodation accommodation, boolean activeRoomsOnly) {
        Long id = accommodation.getId();
        List<String> imageUrls = imageRepository.findByAccommodationIdOrderBySortOrderAsc(id).stream()
                .map(AccommodationImage::getImageUrl).toList();

        List<Long> amenityIds = accommodationAmenityRepository.findByAccommodationId(id).stream()
                .map(AccommodationAmenity::getAmenityId).toList();
        List<AmenityResponse> amenities = amenityRepository.findAllById(amenityIds).stream()
                .sorted(Comparator.comparing(Amenity::getId))
                .map(a -> new AmenityResponse(a.getId(), a.getName()))
                .toList();

        List<Room> rooms = roomRepository.findByAccommodationIdOrderByIdAsc(id).stream()
                .filter(r -> !activeRoomsOnly || r.isActive())
                .toList();
        Map<Long, List<String>> roomImages = rooms.isEmpty() ? Map.of()
                : roomImageRepository.findByRoomIdInOrderBySortOrderAsc(rooms.stream().map(Room::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(RoomImage::getRoomId,
                        Collectors.mapping(RoomImage::getImageUrl, Collectors.toList())));

        return new AccommodationDetail(
                id, accommodation.getName(), accommodation.getDescription(), accommodation.getAddress(),
                accommodation.getRegionId(),
                regionRepository.findById(accommodation.getRegionId()).map(Region::getName).orElse(null),
                accommodation.getCategoryId(),
                categoryRepository.findById(accommodation.getCategoryId()).map(AccommodationCategory::getName)
                        .orElse(null),
                accommodation.getLatitude(), accommodation.getLongitude(),
                accommodation.getCheckInTime(), accommodation.getCheckOutTime(),
                accommodation.getListingStatus(), imageUrls, amenities,
                rooms.stream().map(r -> toRoomResponse(r, roomImages.getOrDefault(r.getId(), List.of()))).toList(),
                getActiveRefundPolicy(id).orElse(null));
    }

    public RoomResponse toRoomResponse(Room room, List<String> imageUrls) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getMaxCapacity(),
                room.getExtraPersonFee(), room.getNightlyPrice(), room.getStatus(), imageUrls);
    }

    // ---------- 다른 feature용 ----------

    /** 현재 적용 중인 환불정책과 규칙 (일수 내림차순). */
    public Optional<RefundPolicyResponse> getActiveRefundPolicy(Long accommodationId) {
        return refundPolicyRepository.findFirstByAccommodationIdAndActiveTrueOrderByIdDesc(accommodationId)
                .map(this::toPolicyResponse);
    }

    public RefundPolicyResponse toPolicyResponse(RefundPolicy policy) {
        List<RefundRule> rules = refundPolicyRuleRepository
                .findByRefundPolicyIdOrderByDaysBeforeDesc(policy.getId()).stream()
                .map(r -> new RefundRule(r.getDaysBefore(), r.getRefundRate()))
                .toList();
        return new RefundPolicyResponse(policy.getId(), policy.getVersion(), policy.getPolicyText(), rules);
    }

    public RoomInfo getRoomInfo(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ROOM_NOT_FOUND));
        Accommodation accommodation = accommodationRepository.findById(room.getAccommodationId())
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ACCOMMODATION_NOT_FOUND));
        return toRoomInfo(room, accommodation);
    }

    /** 숙소의 판매 중인 객실. 숙소가 LISTED가 아니면 없는 숙소로 본다. */
    public List<RoomInfo> getSellableRooms(Long accommodationId) {
        Accommodation accommodation = accommodationRepository.findById(accommodationId)
                .filter(Accommodation::isListed)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ACCOMMODATION_NOT_FOUND));
        return roomRepository.findByAccommodationIdOrderByIdAsc(accommodationId).stream()
                .filter(Room::isActive)
                .map(r -> toRoomInfo(r, accommodation))
                .toList();
    }

    /** 판매 중(ACTIVE)인 모든 객실. 재고 자동 생성 스케줄러가 쓴다. */
    public List<RoomInfo> getAllActiveRooms() {
        List<Room> rooms = roomRepository.findByStatus(Room.RoomStatus.ACTIVE);
        Map<Long, Accommodation> accommodations = accommodationRepository
                .findAllById(rooms.stream().map(Room::getAccommodationId).distinct().toList()).stream()
                .collect(Collectors.toMap(Accommodation::getId, Function.identity()));
        return rooms.stream()
                .filter(r -> accommodations.containsKey(r.getAccommodationId()))
                .map(r -> toRoomInfo(r, accommodations.get(r.getAccommodationId())))
                .toList();
    }

    /** 객실이 그 회원(호스트)의 것인지 검사한다. 아니면 예외. */
    public void assertRoomOwner(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ROOM_NOT_FOUND));
        assertAccommodationOwner(room.getAccommodationId(), userId);
    }

    public void assertAccommodationOwner(Long accommodationId, Long userId) {
        Accommodation accommodation = accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ACCOMMODATION_NOT_FOUND));
        if (!hostService.getBusinessOf(userId).businessId().equals(accommodation.getHostBusinessId())) {
            throw new BaseException(AccommodationErrorCode.NOT_OWNER);
        }
    }

    /** 그 회원(호스트)이 가진 모든 숙소의 객실 ID. 호스트의 예약 목록 조회에 쓴다. */
    public List<Long> getRoomIdsOfHost(Long userId) {
        List<Long> accommodationIds = accommodationRepository
                .findByHostBusinessId(hostService.getBusinessOf(userId).businessId()).stream()
                .map(Accommodation::getId).toList();
        if (accommodationIds.isEmpty()) {
            return List.of();
        }
        return roomRepository.findByAccommodationIdIn(accommodationIds).stream().map(Room::getId).toList();
    }

    public boolean existsListed(Long accommodationId) {
        return accommodationRepository.findById(accommodationId).filter(Accommodation::isListed).isPresent();
    }

    private RoomInfo toRoomInfo(Room room, Accommodation accommodation) {
        return new RoomInfo(room.getId(), accommodation.getId(), accommodation.getName(), room.getName(),
                room.getCapacity(), room.getMaxCapacity(), room.getExtraPersonFee(), room.getNightlyPrice(),
                room.isActive() && accommodation.isListed());
    }
}

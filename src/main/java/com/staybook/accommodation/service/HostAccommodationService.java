package com.staybook.accommodation.service;

import com.staybook.accommodation.domain.Accommodation;
import com.staybook.accommodation.domain.Accommodation.ListingStatus;
import com.staybook.accommodation.domain.AccommodationAmenity;
import com.staybook.accommodation.domain.AccommodationImage;
import com.staybook.accommodation.domain.RefundPolicy;
import com.staybook.accommodation.domain.RefundPolicyRule;
import com.staybook.accommodation.domain.Room;
import com.staybook.accommodation.domain.RoomImage;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationDetail;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationUpdateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RefundRule;
import com.staybook.accommodation.dto.AccommodationDtos.RoomCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RoomResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomUpdateRequest;
import com.staybook.accommodation.exception.AccommodationErrorCode;
import com.staybook.accommodation.repository.AccommodationAmenityRepository;
import com.staybook.accommodation.repository.AccommodationImageRepository;
import com.staybook.accommodation.repository.AccommodationRepository;
import com.staybook.accommodation.repository.AmenityRepository;
import com.staybook.accommodation.repository.RefundPolicyRepository;
import com.staybook.accommodation.repository.RefundPolicyRuleRepository;
import com.staybook.accommodation.repository.RegionRepository;
import com.staybook.accommodation.repository.RoomImageRepository;
import com.staybook.accommodation.repository.RoomRepository;
import com.staybook.common.exception.BaseException;
import com.staybook.host.service.HostService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 호스트의 숙소·객실·환불정책 관리. 모든 변경은 본인 소유인지 먼저 검사한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class HostAccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationImageRepository imageRepository;
    private final AccommodationAmenityRepository accommodationAmenityRepository;
    private final AmenityRepository amenityRepository;
    private final RegionRepository regionRepository;
    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final RefundPolicyRuleRepository refundPolicyRuleRepository;
    private final AccommodationQueryService queryService;
    private final HostService hostService;
    private final Clock clock;

    // ---------- 숙소 ----------

    /** 숙소 등록. DRAFT로 만들어지고, 객실과 환불정책을 등록한 뒤 LISTED로 바꿔 공개한다. */
    public AccommodationDetail create(Long userId, AccommodationCreateRequest request) {
        Long businessId = hostService.getBusinessOf(userId).businessId();
        validateRegion(request.regionId());

        Accommodation accommodation = accommodationRepository.save(Accommodation.create(
                businessId, request.regionId(), request.name().trim(), request.description(),
                request.address().trim(), request.latitude(), request.longitude(),
                request.checkInTime(), request.checkOutTime(), LocalDateTime.now(clock)));
        replaceImages(accommodation.getId(), request.imageUrls());
        replaceAmenities(accommodation.getId(), request.amenityIds());
        return queryService.toDetail(accommodation, false);
    }

    public AccommodationDetail update(Long userId, Long accommodationId, AccommodationUpdateRequest request) {
        Accommodation accommodation = loadOwned(accommodationId, userId);
        if (request.regionId() != null) {
            validateRegion(request.regionId());
        }
        accommodation.update(request.regionId(), trim(request.name()), request.description(),
                trim(request.address()), request.latitude(), request.longitude(),
                request.checkInTime(), request.checkOutTime());
        if (request.imageUrls() != null) {
            replaceImages(accommodationId, request.imageUrls());
        }
        if (request.amenityIds() != null) {
            replaceAmenities(accommodationId, request.amenityIds());
        }
        if (request.listingStatus() != null) {
            if (request.listingStatus() == ListingStatus.LISTED) {
                assertReadyToList(accommodationId);
            }
            accommodation.changeListingStatus(request.listingStatus());
        }
        return queryService.toDetail(accommodation, false);
    }

    // ---------- 객실 ----------

    public RoomResponse addRoom(Long userId, Long accommodationId, RoomCreateRequest request) {
        loadOwned(accommodationId, userId);
        validateCapacity(request.capacity(), request.maxCapacity());

        Room room = roomRepository.save(Room.create(accommodationId, request.name().trim(),
                request.capacity(), request.maxCapacity(), request.extraPersonFee(), request.nightlyPrice()));
        List<String> imageUrls = replaceRoomImages(room.getId(), request.imageUrls());
        return queryService.toRoomResponse(room, imageUrls);
    }

    public RoomResponse updateRoom(Long userId, Long accommodationId, Long roomId, RoomUpdateRequest request) {
        loadOwned(accommodationId, userId);
        Room room = roomRepository.findById(roomId)
                .filter(r -> r.getAccommodationId().equals(accommodationId))
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ROOM_NOT_FOUND));

        room.update(trim(request.name()), request.capacity(), request.maxCapacity(),
                request.extraPersonFee(), request.nightlyPrice(), request.status());
        validateCapacity(room.getCapacity(), room.getMaxCapacity());

        List<String> imageUrls = request.imageUrls() != null
                ? replaceRoomImages(roomId, request.imageUrls())
                : roomImageRepository.findByRoomIdInOrderBySortOrderAsc(List.of(roomId)).stream()
                .map(RoomImage::getImageUrl).toList();
        return queryService.toRoomResponse(room, imageUrls);
    }

    // ---------- 환불정책 ----------

    /**
     * 환불정책 설정. 기존 정책은 고치지 않고 비활성으로 돌린 뒤 새 버전을 만든다.
     * 기존 예약이 과거 버전을 참조하기 때문이다.
     */
    public RefundPolicyResponse putRefundPolicy(Long userId, Long accommodationId, RefundPolicyRequest request) {
        loadOwned(accommodationId, userId);
        Set<Integer> days = new LinkedHashSet<>();
        for (RefundRule rule : request.rules()) {
            if (!days.add(rule.daysBefore())) {
                throw new BaseException(AccommodationErrorCode.DUPLICATE_REFUND_RULE);
            }
        }

        refundPolicyRepository.findByAccommodationIdAndActiveTrue(accommodationId)
                .forEach(RefundPolicy::deactivate);
        String version = "v" + (refundPolicyRepository.countByAccommodationId(accommodationId) + 1);
        RefundPolicy policy = refundPolicyRepository.save(
                RefundPolicy.createActive(accommodationId, version, request.policyText()));
        refundPolicyRuleRepository.saveAll(request.rules().stream()
                .map(r -> RefundPolicyRule.of(policy.getId(), r.daysBefore(), r.refundRate()))
                .toList());
        return queryService.toPolicyResponse(policy);
    }

    // ---------- 내부 ----------

    /** 호스트 API는 리소스가 본인 소유인지 반드시 검사한다. */
    private Accommodation loadOwned(Long accommodationId, Long userId) {
        Accommodation accommodation = accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new BaseException(AccommodationErrorCode.ACCOMMODATION_NOT_FOUND));
        if (!hostService.getBusinessOf(userId).businessId().equals(accommodation.getHostBusinessId())) {
            throw new BaseException(AccommodationErrorCode.NOT_OWNER);
        }
        return accommodation;
    }

    private void assertReadyToList(Long accommodationId) {
        boolean hasRoom = roomRepository.existsByAccommodationIdAndStatus(accommodationId, Room.RoomStatus.ACTIVE);
        boolean hasPolicy = !refundPolicyRepository.findByAccommodationIdAndActiveTrue(accommodationId).isEmpty();
        if (!hasRoom || !hasPolicy) {
            throw new BaseException(AccommodationErrorCode.NOT_READY_TO_LIST);
        }
    }

    private void validateRegion(Long regionId) {
        if (!regionRepository.existsById(regionId)) {
            throw new BaseException(AccommodationErrorCode.REGION_NOT_FOUND);
        }
    }

    private void validateCapacity(int capacity, int maxCapacity) {
        if (maxCapacity < capacity) {
            throw new BaseException(AccommodationErrorCode.INVALID_CAPACITY);
        }
    }

    private void replaceImages(Long accommodationId, List<String> imageUrls) {
        imageRepository.deleteAllByAccommodationId(accommodationId);
        if (imageUrls == null) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            imageRepository.save(AccommodationImage.of(accommodationId, imageUrls.get(i), i));
        }
    }

    private List<String> replaceRoomImages(Long roomId, List<String> imageUrls) {
        roomImageRepository.deleteAllByRoomId(roomId);
        if (imageUrls == null) {
            return List.of();
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            roomImageRepository.save(RoomImage.of(roomId, imageUrls.get(i), i));
        }
        return imageUrls;
    }

    private void replaceAmenities(Long accommodationId, List<Long> amenityIds) {
        accommodationAmenityRepository.deleteAllByAccommodationId(accommodationId);
        if (amenityIds == null) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(amenityIds);
        if (amenityRepository.findAllById(distinct).size() != distinct.size()) {
            throw new BaseException(AccommodationErrorCode.AMENITY_NOT_FOUND);
        }
        distinct.forEach(id -> accommodationAmenityRepository.save(AccommodationAmenity.of(accommodationId, id)));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

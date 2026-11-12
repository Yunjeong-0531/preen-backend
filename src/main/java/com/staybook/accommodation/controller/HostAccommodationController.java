package com.staybook.accommodation.controller;

import com.staybook.accommodation.dto.AccommodationDtos.AccommodationCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationDetail;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationUpdateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.HostAccommodationSummary;
import com.staybook.accommodation.dto.AccommodationDtos.ListingStatusRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RoomResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomUpdateRequest;
import com.staybook.accommodation.service.HostAccommodationService;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 호스트 전용 (/api/host/** 는 HOST만 접근). 소유 여부는 서비스에서 검사한다. */
@RestController
@RequestMapping("/api/host/accommodations")
@RequiredArgsConstructor
public class HostAccommodationController {

    private final HostAccommodationService hostAccommodationService;

    @PostMapping
    public ApiResponse<AccommodationDetail> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody AccommodationCreateRequest request) {
        return ApiResponse.ok(hostAccommodationService.create(authUser.userId(), request));
    }

    /** 내 숙소 목록. 공개 상태(DRAFT·LISTED·HIDDEN)와 관계없이 모두 나온다. */
    @GetMapping
    public ApiResponse<List<HostAccommodationSummary>> getMine(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(hostAccommodationService.getMine(authUser.userId()));
    }

    /** 내 숙소 상세. 공개 전이거나 숨긴 숙소도 보이고, 판매 중지 객실도 포함한다. */
    @GetMapping("/{id}")
    public ApiResponse<AccommodationDetail> getMyDetail(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(hostAccommodationService.getMyDetail(authUser.userId(), id));
    }

    /** 숙소 세부사항 수정. 공개 상태는 바꾸지 않는다. */
    @PatchMapping("/{id}")
    public ApiResponse<AccommodationDetail> update(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody AccommodationUpdateRequest request) {
        return ApiResponse.ok(hostAccommodationService.update(authUser.userId(), id, request));
    }

    /** 공개 상태 변경 (DRAFT·LISTED·HIDDEN). LISTED는 판매 중인 객실과 환불정책이 있어야 한다. */
    @PatchMapping("/{id}/listing-status")
    public ApiResponse<AccommodationDetail> changeListingStatus(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody ListingStatusRequest request) {
        return ApiResponse.ok(hostAccommodationService.changeListingStatus(authUser.userId(), id,
                request.listingStatus()));
    }

    @PostMapping("/{id}/rooms")
    public ApiResponse<RoomResponse> addRoom(@AuthenticationPrincipal AuthUser authUser,
                                             @PathVariable Long id,
                                             @Valid @RequestBody RoomCreateRequest request) {
        return ApiResponse.ok(hostAccommodationService.addRoom(authUser.userId(), id, request));
    }

    /** 객실 수정·판매 중지. 공개 중인 숙소의 마지막 판매 객실은 중지할 수 없다. */
    @PatchMapping("/{id}/rooms/{roomId}")
    public ApiResponse<RoomResponse> updateRoom(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long id,
                                                @PathVariable Long roomId,
                                                @Valid @RequestBody RoomUpdateRequest request) {
        return ApiResponse.ok(hostAccommodationService.updateRoom(authUser.userId(), id, roomId, request));
    }

    @PutMapping("/{id}/refund-policy")
    public ApiResponse<RefundPolicyResponse> putRefundPolicy(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long id,
                                                             @Valid @RequestBody RefundPolicyRequest request) {
        return ApiResponse.ok(hostAccommodationService.putRefundPolicy(authUser.userId(), id, request));
    }
}

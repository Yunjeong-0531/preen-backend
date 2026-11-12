package com.staybook.accommodation.controller;

import com.staybook.accommodation.dto.AccommodationDtos.AccommodationCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationDetail;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationUpdateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RefundPolicyResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomCreateRequest;
import com.staybook.accommodation.dto.AccommodationDtos.RoomResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RoomUpdateRequest;
import com.staybook.accommodation.service.HostAccommodationService;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import jakarta.validation.Valid;
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

    @GetMapping
    public ApiResponse<java.util.List<AccommodationDetail>> getMyAccommodations(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(hostAccommodationService.getMyAccommodations(authUser.userId()));
    }

    @PostMapping
    public ApiResponse<AccommodationDetail> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody AccommodationCreateRequest request) {
        return ApiResponse.ok(hostAccommodationService.create(authUser.userId(), request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AccommodationDetail> update(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody AccommodationUpdateRequest request) {
        return ApiResponse.ok(hostAccommodationService.update(authUser.userId(), id, request));
    }

    @PostMapping("/{id}/rooms")
    public ApiResponse<RoomResponse> addRoom(@AuthenticationPrincipal AuthUser authUser,
                                             @PathVariable Long id,
                                             @Valid @RequestBody RoomCreateRequest request) {
        return ApiResponse.ok(hostAccommodationService.addRoom(authUser.userId(), id, request));
    }

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

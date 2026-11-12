package com.staybook.reservation.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.response.PageResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.reservation.dto.ReservationDtos.CancelResponse;
import com.staybook.reservation.dto.ReservationDtos.CreateRequest;
import com.staybook.reservation.dto.ReservationDtos.ReservationResponse;
import com.staybook.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 예약 생성 (PENDING + 재고 선점). 응답의 holdExpiresAt까지 결제해야 한다. */
    @PostMapping("/api/reservations")
    public ApiResponse<ReservationResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(reservationService.create(authUser.userId(), request));
    }

    @GetMapping("/api/reservations/me")
    public ApiResponse<PageResponse<ReservationResponse>> getMine(@AuthenticationPrincipal AuthUser authUser,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reservationService.getMine(authUser.userId(), page, size));
    }

    @GetMapping("/api/reservations/{id}")
    public ApiResponse<ReservationResponse> getDetail(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable Long id) {
        return ApiResponse.ok(reservationService.getDetail(authUser.userId(), id));
    }

    @PostMapping("/api/reservations/{id}/cancel")
    public ApiResponse<CancelResponse> cancel(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ApiResponse.ok(reservationService.cancel(authUser.userId(), id));
    }

    /** 호스트 본인 숙소의 예약 목록. HOST 전용. */
    @GetMapping("/api/host/reservations")
    public ApiResponse<PageResponse<ReservationResponse>> getForHost(@AuthenticationPrincipal AuthUser authUser,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reservationService.getForHost(authUser.userId(), page, size));
    }
}

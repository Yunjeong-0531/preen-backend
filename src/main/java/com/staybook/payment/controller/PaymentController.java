package com.staybook.payment.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.payment.dto.PaymentDtos.ConfirmRequest;
import com.staybook.payment.dto.PaymentDtos.PaymentResponse;
import com.staybook.payment.dto.PaymentDtos.PrepareRequest;
import com.staybook.payment.dto.PaymentDtos.PrepareResponse;
import com.staybook.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 결제 준비. order_id 발급, READY 저장. */
    @PostMapping("/prepare")
    public ApiResponse<PrepareResponse> prepare(@AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody PrepareRequest request) {
        return ApiResponse.ok(paymentService.prepare(authUser.userId(), request));
    }

    /** PG 승인 요청. 성공 시 PAID 및 예약 확정. */
    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirm(@AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(paymentService.confirm(authUser.userId(), request));
    }
}

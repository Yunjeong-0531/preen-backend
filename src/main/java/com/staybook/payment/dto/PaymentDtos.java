package com.staybook.payment.dto;

import com.staybook.payment.domain.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** payment 요청·응답 모음. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PrepareRequest(@NotNull(message = "예약을 선택해 주세요.") Long reservationId) {
    }

    /**
     * 결제창을 띄울 때 쓰는 값.
     *
     * @param orderId   PG에 넘기는 주문 번호(멱등키)
     * @param orderName 결제창에 표시할 이름
     */
    public record PrepareResponse(String orderId, BigDecimal amount, String orderName, String pgProvider) {
    }

    /** 결제창이 돌려준 값. 금액은 서버가 예약 금액과 다시 비교한다. */
    public record ConfirmRequest(
            @NotBlank(message = "주문 번호가 필요합니다.") @Size(max = 64) String orderId,
            @NotBlank(message = "결제 키가 필요합니다.") @Size(max = 200) String paymentKey,
            @NotNull(message = "결제 금액이 필요합니다.") @DecimalMin("0") BigDecimal amount
    ) {
    }

    public record PaymentResponse(String orderId, Long reservationId, BigDecimal amount, String method,
                                  String status, LocalDateTime paidAt) {

        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getOrderId(), payment.getReservationId(), payment.getAmount(),
                    payment.getMethod(), payment.getStatus().name(), payment.getPaidAt());
        }
    }
}

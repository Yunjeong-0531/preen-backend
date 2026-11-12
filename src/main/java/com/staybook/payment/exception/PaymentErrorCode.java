package com.staybook.payment.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 정보를 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "PAYMENT-002", "본인의 예약만 결제할 수 있습니다."),
    RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "PAYMENT-003", "결제할 수 없는 예약 상태입니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT-004", "결제 금액이 예약 금액과 다릅니다."),
    PG_APPROVAL_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT-005", "결제 승인에 실패했습니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT-006", "이미 처리가 끝난 결제입니다. 결제를 다시 준비해 주세요."),
    RESERVATION_EXPIRED_REFUNDED(HttpStatus.CONFLICT, "PAYMENT-007",
            "결제 제한 시간이 지나 예약이 취소되었습니다. 결제 금액은 전액 환불됩니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "PAYMENT-008", "웹훅 서명이 올바르지 않습니다."),
    INVALID_WEBHOOK_PAYLOAD(HttpStatus.BAD_REQUEST, "PAYMENT-009", "웹훅 본문을 해석할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

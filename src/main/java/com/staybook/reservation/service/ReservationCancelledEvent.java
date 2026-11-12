package com.staybook.reservation.service;

import java.math.BigDecimal;

/**
 * 결제까지 끝난(CONFIRMED) 예약이 취소됐다. payment가 커밋 이후에 받아 환불한다.
 *
 * @param refundRate 예약의 환불정책 스냅샷에서 구한 환불 비율 (0~1). 결제 금액에 곱한 값이 환불액이다
 */
public record ReservationCancelledEvent(Long reservationId, BigDecimal refundRate, String reason) {
}

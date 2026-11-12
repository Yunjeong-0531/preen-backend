package com.staybook.payment.service;

import com.staybook.reservation.service.ReservationCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 예약 취소가 커밋된 이후에 환불을 처리한다. 환불이 실패해도 예약 취소는 그대로 유지된다. */
@Component
@RequiredArgsConstructor
public class ReservationCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationCancelledListener.class);

    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelledEvent event) {
        try {
            paymentService.refundForCancellation(event);
        } catch (RuntimeException e) {
            log.error("예약 취소 환불 처리 실패: reservationId={}", event.reservationId(), e);
        }
    }
}

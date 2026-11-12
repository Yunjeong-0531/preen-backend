package com.staybook.reservation.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReservationService reservationService;

    /** 1분마다: 결제 제한 시간이 지난 PENDING 예약을 취소한다. */
    @Scheduled(fixedDelayString = "${staybook.reservation.expire-interval:60000}")
    public void expirePending() {
        int expired = reservationService.expirePending();
        if (expired > 0) {
            log.info("선점 만료로 취소한 예약: {}건", expired);
        }
    }

    /** 매일 한 번: 체크아웃이 지난 CONFIRMED 예약을 COMPLETED로 바꾼다. */
    @Scheduled(cron = "${staybook.reservation.complete-cron:0 20 0 * * *}", zone = "Asia/Seoul")
    public void completeStays() {
        int completed = reservationService.completeStays();
        log.info("투숙 완료 처리한 예약: {}건", completed);
    }
}

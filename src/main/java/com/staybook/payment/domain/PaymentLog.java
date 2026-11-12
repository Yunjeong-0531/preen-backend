package com.staybook.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PG 응답·이벤트 로그. 우리가 호출한 승인·취소의 응답과, PG가 보낸 웹훅을 모두 남긴다.
 * 웹훅은 provider_event_id 유니크로 같은 이벤트가 두 번 처리되는 것을 막는다.
 */
@Entity
@Table(name = "payment_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLog {

    public static final String EVENT_APPROVE = "APPROVE";
    public static final String EVENT_CANCEL = "CANCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(length = 100)
    private String providerEventId;

    @Column(length = 50)
    private String responseCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static PaymentLog of(Long paymentId, String eventType, String providerEventId, String responseCode,
                                LocalDateTime now) {
        PaymentLog log = new PaymentLog();
        log.paymentId = paymentId;
        log.eventType = truncate(eventType, 50);
        log.providerEventId = providerEventId;
        log.responseCode = truncate(responseCode, 50);
        log.createdAt = now;
        return log;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}

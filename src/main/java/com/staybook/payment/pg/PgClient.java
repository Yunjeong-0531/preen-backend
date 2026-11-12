package com.staybook.payment.pg;

import java.math.BigDecimal;
import java.util.Map;

/**
 * PG 연동 지점. 실제 PG사 구현체(TossPaymentsPgClient)와 테스트용 가짜 구현체(FakePgClient)를 분리한다.
 * 구현체는 통신 오류도 예외로 던지지 않고 실패 결과로 돌려준다.
 */
public interface PgClient {

    /** payments.pg_provider에 저장하는 이름. */
    String provider();

    /** 결제 승인 요청. */
    Approval approve(String orderId, String paymentKey, BigDecimal amount);

    /** 결제 취소(환불). amount만큼 부분 취소한다. */
    Cancellation cancel(String paymentKey, BigDecimal amount, String reason);

    /**
     * 웹훅이 PG가 보낸 것인지 서명으로 검증한다.
     *
     * @param headers 헤더 이름은 소문자
     */
    boolean verifyWebhook(String rawBody, Map<String, String> headers);

    /** 웹훅 본문을 해석한다. 해석할 수 없으면 null. */
    WebhookEvent parseWebhook(String rawBody, Map<String, String> headers);

    /**
     * @param responseCode PG 응답 코드. PAYMENT_LOGS에 남긴다
     */
    record Approval(boolean success, String paymentKey, String method, String responseCode) {

        public static Approval failed(String responseCode) {
            return new Approval(false, null, null, responseCode);
        }
    }

    record Cancellation(boolean success, String responseCode) {
    }

    enum WebhookStatus {
        PAID,
        FAILED,
        CANCELLED,
        OTHER
    }

    /**
     * @param eventId PG가 준 이벤트 식별자. 같은 웹훅이 두 번 처리되지 않게 하는 데 쓴다
     */
    record WebhookEvent(String eventId, String eventType, String orderId, String paymentKey, String method,
                        WebhookStatus status, String responseCode) {
    }
}

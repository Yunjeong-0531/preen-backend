package com.staybook.payment.pg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 테스트·로컬 개발용 가짜 PG. staybook.pg.provider=fake일 때만 쓰인다. 실제 돈은 움직이지 않는다.
 * <ul>
 *   <li>승인: paymentKey가 "fail"로 시작하면 실패, 그 외에는 성공</li>
 *   <li>취소: paymentKey에 "refund-fail"이 들어 있으면 실패, 그 외에는 성공</li>
 *   <li>웹훅 서명: 헤더 x-fake-signature = HMAC-SHA256(본문, webhook-secret)의 hex</li>
 *   <li>웹훅 본문: {"eventId","eventType","orderId","paymentKey","status": "PAID|FAILED|CANCELLED"}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "staybook.pg", name = "provider", havingValue = "fake")
public class FakePgClient implements PgClient {

    public static final String SIGNATURE_HEADER = "x-fake-signature";

    private final PgProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicInteger approveCalls = new AtomicInteger();
    private final AtomicInteger cancelCalls = new AtomicInteger();

    public FakePgClient(PgProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "FAKE";
    }

    @Override
    public Approval approve(String orderId, String paymentKey, BigDecimal amount) {
        approveCalls.incrementAndGet();
        if (paymentKey.startsWith("fail")) {
            return Approval.failed("REJECT_CARD_COMPANY");
        }
        return new Approval(true, paymentKey, "CARD", "DONE");
    }

    @Override
    public Cancellation cancel(String paymentKey, BigDecimal amount, String reason) {
        cancelCalls.incrementAndGet();
        if (paymentKey.contains("refund-fail")) {
            return new Cancellation(false, "CANCEL_FAILED");
        }
        return new Cancellation(true, "CANCELED");
    }

    @Override
    public boolean verifyWebhook(String rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (signature == null || properties.webhookSecret().isBlank()) {
            return false;
        }
        try {
            return WebhookSignatures.constantTimeEquals(
                    WebhookSignatures.hmacSha256(properties.webhookSecret(), rawBody),
                    HexFormat.of().parseHex(signature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (!node.hasNonNull("eventId") || !node.hasNonNull("orderId")) {
                return null;
            }
            WebhookStatus status = switch (node.path("status").asText("")) {
                case "PAID" -> WebhookStatus.PAID;
                case "FAILED" -> WebhookStatus.FAILED;
                case "CANCELLED" -> WebhookStatus.CANCELLED;
                default -> WebhookStatus.OTHER;
            };
            return new WebhookEvent(node.get("eventId").asText(), node.path("eventType").asText("UNKNOWN"),
                    node.get("orderId").asText(), node.path("paymentKey").asText(null), "CARD", status,
                    node.path("status").asText(null));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public int approveCalls() {
        return approveCalls.get();
    }

    public int cancelCalls() {
        return cancelCalls.get();
    }
}

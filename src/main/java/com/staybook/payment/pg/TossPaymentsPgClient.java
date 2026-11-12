package com.staybook.payment.pg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staybook.common.crypto.HashUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토스페이먼츠 구현체. staybook.pg.provider=toss(기본값)일 때 쓰인다.
 * 시크릿 키(PG_SECRET_KEY)와 웹훅 시크릿(PG_WEBHOOK_SECRET)은 환경 변수로 받는다.
 * <p>
 * 주의: 실제 키로 호출해 검증하지 못한 구현이다. 운영 전에 토스페이먼츠 문서로 승인·취소 API와
 * 웹훅 서명 방식을 확인하고 테스트 키로 점검해야 한다.
 */
@Component
@ConditionalOnProperty(prefix = "staybook.pg", name = "provider", havingValue = "toss", matchIfMissing = true)
public class TossPaymentsPgClient implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentsPgClient.class);

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SIGNATURE_HEADER = "tosspayments-webhook-signature";
    private static final String TRANSMISSION_TIME_HEADER = "tosspayments-webhook-transmission-time";
    private static final String TRANSMISSION_ID_HEADER = "tosspayments-webhook-transmission-id";
    private static final String SIGNATURE_VERSION_PREFIX = "v1:";

    private final PgProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TossPaymentsPgClient(PgProperties properties, ObjectMapper objectMapper,
                                RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        if (properties.secretKey().isBlank()) {
            log.warn("PG_SECRET_KEY가 없어 결제 승인이 모두 실패합니다. 로컬 개발에서는 PG_PROVIDER=fake를 쓸 수 있습니다.");
        }
    }

    @Override
    public String provider() {
        return "TOSS";
    }

    @Override
    public Approval approve(String orderId, String paymentKey, BigDecimal amount) {
        if (properties.secretKey().isBlank()) {
            return Approval.failed("PG_NOT_CONFIGURED");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount.toBigInteger());
        try {
            JsonNode response = restClient.post().uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String status = text(response, "status");
            if (!"DONE".equals(status)) {
                return Approval.failed(status == null ? "UNKNOWN" : status);
            }
            return new Approval(true, text(response, "paymentKey"), text(response, "method"), status);
        } catch (RestClientResponseException e) {
            return Approval.failed(errorCode(e));
        } catch (RestClientException e) {
            log.error("토스페이먼츠 승인 요청 실패", e);
            return Approval.failed("PG_COMMUNICATION_ERROR");
        }
    }

    @Override
    public Cancellation cancel(String paymentKey, BigDecimal amount, String reason) {
        if (properties.secretKey().isBlank()) {
            return new Cancellation(false, "PG_NOT_CONFIGURED");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason);
        body.put("cancelAmount", amount.toBigInteger());
        try {
            JsonNode response = restClient.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String status = text(response, "status");
            return new Cancellation(true, status == null ? "CANCELED" : status);
        } catch (RestClientResponseException e) {
            return new Cancellation(false, errorCode(e));
        } catch (RestClientException e) {
            log.error("토스페이먼츠 취소 요청 실패", e);
            return new Cancellation(false, "PG_COMMUNICATION_ERROR");
        }
    }

    /** 서명 = Base64(HMAC-SHA256("{본문}:{전송 시각}", 웹훅 시크릿)). 헤더에는 "v1:" 접두사와 함께 쉼표로 여러 개가 올 수 있다. */
    @Override
    public boolean verifyWebhook(String rawBody, Map<String, String> headers) {
        String signatures = headers.get(SIGNATURE_HEADER);
        String transmissionTime = headers.get(TRANSMISSION_TIME_HEADER);
        if (signatures == null || transmissionTime == null || properties.webhookSecret().isBlank()) {
            return false;
        }
        byte[] expected = WebhookSignatures.hmacSha256(properties.webhookSecret(), rawBody + ":" + transmissionTime);
        for (String candidate : signatures.split(",")) {
            String value = candidate.trim();
            if (!value.startsWith(SIGNATURE_VERSION_PREFIX)) {
                continue;
            }
            try {
                byte[] actual = Base64.getDecoder().decode(value.substring(SIGNATURE_VERSION_PREFIX.length()));
                if (WebhookSignatures.constantTimeEquals(expected, actual)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Base64가 아닌 값은 건너뛴다
            }
        }
        return false;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            JsonNode data = node.path("data");
            String orderId = text(data, "orderId");
            if (orderId == null) {
                return null;
            }
            String pgStatus = text(data, "status");
            WebhookStatus status = switch (pgStatus == null ? "" : pgStatus) {
                case "DONE" -> WebhookStatus.PAID;
                case "ABORTED", "EXPIRED" -> WebhookStatus.FAILED;
                case "CANCELED", "PARTIAL_CANCELED" -> WebhookStatus.CANCELLED;
                default -> WebhookStatus.OTHER;
            };
            // 전송 ID가 없으면 본문 해시를 이벤트 식별자로 쓴다
            String eventId = headers.getOrDefault(TRANSMISSION_ID_HEADER, HashUtils.sha256Hex(rawBody));
            String eventType = text(node, "eventType");
            return new WebhookEvent(eventId, eventType == null ? "UNKNOWN" : eventType, orderId,
                    text(data, "paymentKey"), text(data, "method"), status, pgStatus);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }

    private String errorCode(RestClientResponseException e) {
        try {
            String code = text(objectMapper.readTree(e.getResponseBodyAsString()), "code");
            return code != null ? code : "HTTP_" + e.getStatusCode().value();
        } catch (JsonProcessingException ex) {
            return "HTTP_" + e.getStatusCode().value();
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}

package com.staybook.payment.pg;

import com.staybook.common.response.ApiResponse;
import com.staybook.payment.service.PaymentService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 웹훅 수신. 토큰 없이 열려 있으므로 서명 검증으로 PG가 보낸 것인지 확인한다.
 * 서명은 원문 그대로 계산해야 해서 본문을 문자열로 받는다.
 */
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/api/payments/webhook")
    public ApiResponse<Void> receive(@RequestBody String rawBody, @RequestHeader Map<String, String> headers) {
        Map<String, String> lowerCased = new HashMap<>();
        headers.forEach((name, value) -> lowerCased.put(name.toLowerCase(Locale.ROOT), value));
        paymentService.handleWebhook(rawBody, lowerCased);
        return ApiResponse.ok();
    }
}

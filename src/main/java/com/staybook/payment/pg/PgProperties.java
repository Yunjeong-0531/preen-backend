package com.staybook.payment.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PG 키는 코드나 저장소에 넣지 않고 환경 변수로 받는다.
 *
 * @param provider      toss(실제 PG) 또는 fake(테스트·로컬 개발용 가짜)
 * @param secretKey     PG 시크릿 키 (PG_SECRET_KEY)
 * @param webhookSecret 웹훅 서명 검증용 키 (PG_WEBHOOK_SECRET)
 */
@ConfigurationProperties(prefix = "staybook.pg")
public record PgProperties(
        @DefaultValue("toss") String provider,
        @DefaultValue("") String secretKey,
        @DefaultValue("") String webhookSecret
) {
}

package com.staybook.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러(재고 생성, 선점 만료, 완료 처리)를 켠다.
 * staybook.scheduling.enabled=false면 꺼지며, 테스트에서 스케줄러를 직접 호출할 때 쓴다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "staybook.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

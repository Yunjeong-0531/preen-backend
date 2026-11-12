package com.staybook.reservation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param holdDuration 예약 생성 후 결제를 마쳐야 하는 시간. 지나면 스케줄러가 취소하고 재고를 되돌린다
 */
@ConfigurationProperties(prefix = "staybook.reservation")
public record ReservationProperties(@DefaultValue("10m") Duration holdDuration) {
}

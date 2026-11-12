package com.staybook.inventory.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param generateDays      재고 자동 생성 범위. 오늘부터 이 일수 뒤까지 만든다
 * @param defaultTotalCount 재고가 하나도 없는 객실에 처음 만들 때의 수량
 */
@ConfigurationProperties(prefix = "staybook.inventory")
public record InventoryProperties(
        @DefaultValue("90") int generateDays,
        @DefaultValue("1") int defaultTotalCount
) {
}

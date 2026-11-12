package com.staybook.inventory.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 재고 자동 생성. 매일 한 번, 오늘부터 N일(기본 90일) 뒤까지 없는 날짜의 행을 만든다. */
@Component
@RequiredArgsConstructor
public class InventoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryScheduler.class);

    private final InventoryService inventoryService;

    @Scheduled(cron = "${staybook.inventory.generate-cron:0 10 3 * * *}", zone = "Asia/Seoul")
    public void generateUpcoming() {
        int created = inventoryService.generateUpcoming();
        log.info("재고 자동 생성: {}행", created);
    }
}

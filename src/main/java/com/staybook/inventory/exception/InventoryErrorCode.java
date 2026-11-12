package com.staybook.inventory.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {

    SOLD_OUT(HttpStatus.CONFLICT, "INVENTORY-001", "선택한 날짜에 예약 가능한 객실이 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVENTORY-002", "날짜 범위가 올바르지 않습니다."),
    DATE_RANGE_TOO_LONG(HttpStatus.BAD_REQUEST, "INVENTORY-003", "날짜 범위가 너무 깁니다."),
    NOTHING_TO_UPDATE(HttpStatus.BAD_REQUEST, "INVENTORY-004", "수량, 가격, 판매 마감 중 하나 이상을 입력해 주세요."),
    TOTAL_COUNT_TOO_SMALL(HttpStatus.CONFLICT, "INVENTORY-005",
            "이미 예약되었거나 선점된 수량보다 적게 설정할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

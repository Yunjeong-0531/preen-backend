package com.staybook.accommodation.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccommodationErrorCode implements ErrorCode {

    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOMMODATION-001", "숙소를 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "ACCOMMODATION-002", "본인 소유의 숙소가 아닙니다."),
    REGION_NOT_FOUND(HttpStatus.BAD_REQUEST, "ACCOMMODATION-003", "존재하지 않는 지역입니다."),
    AMENITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "ACCOMMODATION-004", "존재하지 않는 편의시설이 포함되어 있습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOMMODATION-005", "객실을 찾을 수 없습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "ACCOMMODATION-006", "최대 인원은 기준 인원 이상이어야 합니다."),
    NOT_READY_TO_LIST(HttpStatus.BAD_REQUEST, "ACCOMMODATION-007",
            "판매 중인 객실과 환불정책을 등록해야 숙소를 공개할 수 있습니다."),
    DUPLICATE_REFUND_RULE(HttpStatus.BAD_REQUEST, "ACCOMMODATION-008", "같은 일수의 환불 규칙이 중복되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

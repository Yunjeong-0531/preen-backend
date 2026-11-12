package com.staybook.reservation.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION-001", "예약을 찾을 수 없습니다."),
    ADULT_ONLY(HttpStatus.FORBIDDEN, "RESERVATION-002", "예약은 성인만 할 수 있습니다."),
    INVALID_DATES(HttpStatus.BAD_REQUEST, "RESERVATION-003",
            "체크인은 오늘 이후, 체크아웃은 체크인 다음 날 이후여야 합니다."),
    GUEST_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "RESERVATION-004", "객실의 최대 인원을 넘었습니다."),
    ROOM_NOT_AVAILABLE(HttpStatus.CONFLICT, "RESERVATION-005", "예약할 수 없는 객실입니다."),
    NOT_CANCELLABLE(HttpStatus.CONFLICT, "RESERVATION-006", "취소할 수 없는 예약입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

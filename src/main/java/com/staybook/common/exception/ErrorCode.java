package com.staybook.common.exception;

import org.springframework.http.HttpStatus;

/**
 * feature별 enum(AuthErrorCode, ReservationErrorCode 등)이 구현한다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}

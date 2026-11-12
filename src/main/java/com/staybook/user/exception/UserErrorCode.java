package com.staybook.user.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "회원을 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-002", "이미 가입된 이메일입니다."),
    UNDER_AGE(HttpStatus.BAD_REQUEST, "USER-003", "만 14세 미만은 가입할 수 없습니다."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "USER-004", "생년월일이 올바르지 않습니다."),
    SIGNUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "USER-005", "이미 가입이 완료된 회원입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

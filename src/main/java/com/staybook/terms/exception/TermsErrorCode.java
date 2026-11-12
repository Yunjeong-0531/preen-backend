package com.staybook.terms.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements ErrorCode {

    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS-001", "필수 약관에 모두 동의해야 합니다."),
    INVALID_TERM(HttpStatus.BAD_REQUEST, "TERMS-002", "현재 유효하지 않은 약관입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

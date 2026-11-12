package com.staybook.auth.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH-002", "로그인 실패가 반복되어 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-003", "리프레시 토큰이 유효하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH-004", "이메일 인증을 먼저 완료해 주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH-005", "인증코드가 올바르지 않거나 만료되었습니다."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "AUTH-006", "인증 시도 횟수를 넘었습니다. 인증코드를 다시 받아 주세요."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "AUTH-007", "비밀번호 재설정 링크가 유효하지 않거나 만료되었습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH-008", "지원하지 않는 소셜 로그인입니다."),
    OAUTH_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH-009", "소셜 로그인이 설정되지 않았습니다."),
    INVALID_OAUTH_STATE(HttpStatus.BAD_REQUEST, "AUTH-010", "소셜 로그인 요청이 유효하지 않거나 만료되었습니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH-011", "소셜 로그인 제공자와 통신하지 못했습니다."),
    SOCIAL_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH-012",
            "같은 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "AUTH-013", "이미 다른 계정에 연결된 소셜 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

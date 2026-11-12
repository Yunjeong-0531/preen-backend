package com.staybook.auth.dto;

/** auth 응답 본문 모음. */
public final class AuthResponses {

    private AuthResponses() {
    }

    public record Signup(Long userId) {
    }

    public record CheckEmail(boolean available) {
    }

    /**
     * @param accessTokenExpiresIn 액세스 토큰 유효 시간(초)
     * @param signupCompleted      false면 생년월일·약관 동의 입력(가입 완료)이 필요하다
     */
    public record Token(String accessToken, String refreshToken, long accessTokenExpiresIn,
                        boolean signupCompleted) {
    }

    public record AuthorizationUrl(String authorizationUrl) {
    }
}

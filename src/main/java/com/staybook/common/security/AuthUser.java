package com.staybook.common.security;

/**
 * 액세스 토큰에서 꺼낸 로그인 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser}로 받는다.
 */
public record AuthUser(Long userId, UserRole role) {

    public boolean isHost() {
        return role == UserRole.HOST;
    }
}

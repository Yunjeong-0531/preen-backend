package com.staybook.user.dto;

import com.staybook.common.security.UserRole;

/**
 * 다른 feature가 토큰 발급 등에 쓰는 회원 요약. 탈퇴하지 않은 회원에 대해서만 만들어진다.
 *
 * @param signupCompleted 생년월일·약관 동의까지 마쳤는지 (소셜 최초 로그인 직후에는 false)
 */
public record UserAccount(Long userId, UserRole role, boolean signupCompleted) {
}

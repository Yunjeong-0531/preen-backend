package com.staybook.user.service;

/** 회원이 탈퇴했다. auth가 받아 그 회원의 리프레시 토큰을 전부 지운다. */
public record UserWithdrawnEvent(Long userId) {
}

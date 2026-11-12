package com.staybook.auth.domain;

public enum LoginResult {
    SUCCESS,
    WRONG_PASSWORD,
    /** 없는 계정, 탈퇴한 계정, 비밀번호가 없는 소셜 전용 계정 */
    NO_ACCOUNT,
    LOCKED
}

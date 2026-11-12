package com.staybook.common.security;

public enum UserRole {
    GUEST,
    HOST,
    ADMIN;

    /** Spring Security 권한 문자열 (ROLE_ 접두사 포함). */
    public String authority() {
        return "ROLE_" + name();
    }
}

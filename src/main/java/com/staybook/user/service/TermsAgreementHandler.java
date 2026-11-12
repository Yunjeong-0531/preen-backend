package com.staybook.user.service;

import java.util.List;

/**
 * 가입 시 약관 동의를 검사하고 기록하는 연결 지점.
 * 의존 방향이 terms → user이므로 인터페이스는 user에 두고 terms가 구현한다.
 */
public interface TermsAgreementHandler {

    /**
     * 필수 약관에 모두 동의했는지 검사하고, 현재 유효한 약관 버전별로 동의 여부를 기록한다.
     * 필수 약관이 빠졌으면 예외를 던져 가입을 거부한다.
     */
    void agree(Long userId, List<Long> agreedTermIds);
}

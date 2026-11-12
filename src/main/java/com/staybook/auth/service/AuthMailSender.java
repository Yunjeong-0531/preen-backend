package com.staybook.auth.service;

/** 인증 관련 메일 발송. SMTP가 설정돼 있으면 실제로 보내고, 아니면 로그로만 남긴다. */
public interface AuthMailSender {

    void sendVerificationCode(String email, String code);

    void sendPasswordResetLink(String email, String resetLink);
}

package com.staybook.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class AuthMailConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthMailConfig.class);

    /** MAIL_HOST가 있으면 SMTP로 보내고, 없으면 발송하지 않고 로그에만 남긴다(로컬 개발용). */
    @Bean
    public AuthMailSender authMailSender(ObjectProvider<JavaMailSender> mailSender,
                                         @Value("${spring.mail.host:}") String host,
                                         @Value("${staybook.mail.from:no-reply@staybook.local}") String from) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (host.isBlank() || sender == null) {
            log.warn("MAIL_HOST가 설정되지 않아 메일을 실제로 보내지 않고 로그에만 남깁니다.");
            return new LoggingAuthMailSender();
        }
        return new SmtpAuthMailSender(sender, from);
    }

    static class SmtpAuthMailSender implements AuthMailSender {

        private final JavaMailSender mailSender;
        private final String from;

        SmtpAuthMailSender(JavaMailSender mailSender, String from) {
            this.mailSender = mailSender;
            this.from = from;
        }

        @Override
        public void sendVerificationCode(String email, String code) {
            send(email, "[StayBook] 이메일 인증코드",
                    "인증코드: " + code + "\n\n인증코드는 발급 후 잠시 동안만 유효합니다.");
        }

        @Override
        public void sendPasswordResetLink(String email, String resetLink) {
            send(email, "[StayBook] 비밀번호 재설정",
                    "아래 링크에서 비밀번호를 다시 설정해 주세요.\n" + resetLink
                            + "\n\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        }

        private void send(String to, String subject, String text) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            try {
                mailSender.send(message);
            } catch (MailException e) {
                // 발송 실패가 가입·재설정 요청 자체를 실패시키지 않게 한다 (재발송으로 복구)
                log.error("메일 발송 실패: {}", subject, e);
            }
        }
    }

    /** SMTP가 없는 로컬 개발 환경용. 운영에서는 MAIL_HOST를 설정해 이 구현이 쓰이지 않게 한다. */
    static class LoggingAuthMailSender implements AuthMailSender {

        @Override
        public void sendVerificationCode(String email, String code) {
            log.info("[메일 미발송] 인증코드 {} -> {}", code, email);
        }

        @Override
        public void sendPasswordResetLink(String email, String resetLink) {
            log.info("[메일 미발송] 비밀번호 재설정 링크 {} -> {}", resetLink, email);
        }
    }
}

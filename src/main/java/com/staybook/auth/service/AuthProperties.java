package com.staybook.auth.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxFailedLogins         연속 실패가 이 횟수에 이르면 잠근다
 * @param lockDuration            잠금 시간
 * @param verificationCodeTtl     인증코드 유효 시간
 * @param verificationMaxAttempts 인증코드를 틀릴 수 있는 횟수. 넘으면 그 코드는 무효다
 * @param verifiedEmailValidFor   이메일 인증 후 가입까지 허용하는 시간
 * @param passwordResetTokenTtl   비밀번호 재설정 토큰 유효 시간
 * @param passwordResetUrl        재설정 메일에 넣는 프론트 주소 (뒤에 ?token= 이 붙는다)
 */
@ConfigurationProperties(prefix = "staybook.auth")
public record AuthProperties(
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("5m") Duration verificationCodeTtl,
        @DefaultValue("5") int verificationMaxAttempts,
        @DefaultValue("30m") Duration verifiedEmailValidFor,
        @DefaultValue("30m") Duration passwordResetTokenTtl,
        @DefaultValue("http://localhost:3000/reset-password") String passwordResetUrl
) {
}

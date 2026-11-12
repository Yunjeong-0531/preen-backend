package com.staybook.auth.service;

import com.staybook.auth.domain.PasswordResetToken;
import com.staybook.auth.domain.UserCredential;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.auth.repository.PasswordResetTokenRepository;
import com.staybook.auth.repository.UserCredentialRepository;
import com.staybook.common.crypto.HashUtils;
import com.staybook.common.exception.BaseException;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserService userService;
    private final UserCredentialRepository credentialRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailSender mailSender;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * 재설정 링크를 메일로 보낸다. 가입 여부가 드러나지 않도록 계정이 없어도 조용히 끝낸다.
     * 비밀번호가 없는 소셜 전용 계정에도 보내지 않는다.
     */
    @Transactional
    public void requestReset(String email) {
        userService.findAccountByEmail(email)
                .filter(account -> credentialRepository.existsById(account.userId()))
                .ifPresent(account -> {
                    String token = HashUtils.randomToken(TOKEN_BYTES);
                    tokenRepository.save(PasswordResetToken.create(account.userId(), HashUtils.sha256Hex(token),
                            now(), properties.passwordResetTokenTtl()));
                    String link = UriComponentsBuilder.fromUriString(properties.passwordResetUrl())
                            .queryParam("token", token).build().toUriString();
                    mailSender.sendPasswordResetLink(email.trim(), link);
                });
    }

    /** 새 비밀번호로 바꾸고, 토큰을 사용 처리하고, 그 회원의 리프레시 토큰을 전부 지운다. */
    @Transactional
    public void reset(String token, String newPassword) {
        LocalDateTime now = now();
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(HashUtils.sha256Hex(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_RESET_TOKEN));
        UserCredential credential = credentialRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_RESET_TOKEN));

        credential.changePassword(passwordEncoder.encode(newPassword), now);
        resetToken.markUsed(now);
        refreshTokenStore.deleteAll(resetToken.getUserId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

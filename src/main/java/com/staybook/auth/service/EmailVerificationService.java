package com.staybook.auth.service;

import com.staybook.auth.domain.VerificationCode;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.auth.repository.VerificationCodeRepository;
import com.staybook.common.crypto.HashUtils;
import com.staybook.common.crypto.LookupHasher;
import com.staybook.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 전 이메일 인증. 코드 발송(재발송) → 확인 → 가입 순서로 쓰인다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int CODE_DIGITS = 6;

    private final VerificationCodeRepository codeRepository;
    private final LookupHasher lookupHasher;
    private final AuthMailSender mailSender;
    private final AuthProperties properties;
    private final Clock clock;

    /** 새 코드를 발급해 보낸다. 다시 호출하면 이전 코드는 더 이상 쓰이지 않는다. */
    @Transactional
    public void send(String email) {
        String targetHash = lookupHasher.hashEmail(email);
        String code = HashUtils.randomNumericCode(CODE_DIGITS);
        codeRepository.save(VerificationCode.emailSignup(
                targetHash, hashCode(targetHash, code), now(), properties.verificationCodeTtl()));
        mailSender.sendVerificationCode(email.trim(), code);
    }

    /** 실패 횟수를 남겨야 하므로 검증 실패 예외로는 롤백하지 않는다. */
    @Transactional(noRollbackFor = BaseException.class)
    public void verify(String email, String code) {
        String targetHash = lookupHasher.hashEmail(email);
        VerificationCode latest = codeRepository
                .findFirstByTargetHashAndPurposeOrderByIdDesc(targetHash, VerificationCode.PURPOSE_SIGNUP)
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_VERIFICATION_CODE));

        if (latest.isVerified() || latest.isExpired(now())) {
            throw new BaseException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }
        if (latest.getAttemptCount() >= properties.verificationMaxAttempts()) {
            throw new BaseException(AuthErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!HashUtils.matches(targetHash + ":" + code, latest.getCodeHash())) {
            latest.recordFailedAttempt();
            throw new BaseException(AuthErrorCode.INVALID_VERIFICATION_CODE);
        }
        latest.markVerified(now());
    }

    /** 가입 직전에 호출한다. 최근에 인증을 마친 이메일이 아니면 거부한다. */
    @Transactional(readOnly = true)
    public void assertVerified(String email) {
        LocalDateTime now = now();
        boolean verified = codeRepository
                .findFirstByTargetHashAndPurposeOrderByIdDesc(lookupHasher.hashEmail(email), VerificationCode.PURPOSE_SIGNUP)
                .filter(VerificationCode::isVerified)
                .filter(c -> c.getVerifiedAt().plus(properties.verifiedEmailValidFor()).isAfter(now))
                .isPresent();
        if (!verified) {
            throw new BaseException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    /** 6자리 숫자만 해시하면 대입으로 쉽게 풀리므로 대상 해시를 함께 섞는다. */
    private static String hashCode(String targetHash, String code) {
        return HashUtils.sha256Hex(targetHash + ":" + code);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

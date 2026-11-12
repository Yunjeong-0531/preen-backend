package com.staybook.auth.service;

import com.staybook.auth.domain.LoginAttempt;
import com.staybook.auth.domain.LoginResult;
import com.staybook.auth.domain.UserCredential;
import com.staybook.auth.dto.AuthRequests;
import com.staybook.auth.dto.AuthResponses;
import com.staybook.auth.dto.ClientInfo;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.auth.repository.LoginAttemptRepository;
import com.staybook.auth.repository.UserCredentialRepository;
import com.staybook.common.crypto.LookupHasher;
import com.staybook.common.exception.BaseException;
import com.staybook.user.dto.RegisterCommand;
import com.staybook.user.dto.UserAccount;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserCredentialRepository credentialRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final LookupHasher lookupHasher;
    private final AuthProperties properties;
    private final Clock clock;

    /** 이메일 가입. 인증을 마친 이메일만 받는다. 회원·개인정보·약관 동의·비밀번호를 한 트랜잭션으로 만든다. */
    @Transactional
    public Long signup(AuthRequests.Signup request) {
        emailVerificationService.assertVerified(request.email());
        Long userId = userService.register(new RegisterCommand(
                request.name(), request.email(), request.phone(), request.birthDate(), request.agreedTermIds()));
        credentialRepository.save(
                UserCredential.create(userId, passwordEncoder.encode(request.password()), now()));
        return userId;
    }

    public boolean isEmailAvailable(String email) {
        return !userService.existsByEmail(email);
    }

    /**
     * 로그인. 실패 횟수와 시도 기록은 실패했을 때도 남아야 하므로 인증 실패 예외로는 롤백하지 않는다.
     * 계정이 없는 경우와 비밀번호가 틀린 경우는 같은 에러로 응답한다.
     */
    @Transactional(noRollbackFor = BaseException.class)
    public AuthResponses.Token login(AuthRequests.Login request, ClientInfo client) {
        LocalDateTime now = now();
        String loginIdHash = lookupHasher.hashEmail(request.email());

        Optional<UserAccount> account = userService.findAccountByEmail(request.email());
        Optional<UserCredential> credential = account.flatMap(a -> credentialRepository.findById(a.userId()));
        if (credential.isEmpty()) {
            // 없는 계정(또는 비밀번호가 없는 소셜 전용 계정)도 user_id NULL로 기록한다
            passwordEncoder.matches(request.password(), dummyHash()); // 응답 시간으로 계정 유무가 드러나지 않게
            record(null, loginIdHash, client, LoginResult.NO_ACCOUNT, now);
            throw new BaseException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Long userId = account.get().userId();
        UserCredential cred = credential.get();
        cred.clearExpiredLock(now);
        if (cred.isLocked(now)) {
            record(userId, loginIdHash, client, LoginResult.LOCKED, now);
            throw new BaseException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), cred.getPasswordHash())) {
            cred.recordFailure(now, properties.maxFailedLogins(), properties.lockDuration());
            record(userId, loginIdHash, client, LoginResult.WRONG_PASSWORD, now);
            throw new BaseException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        cred.recordSuccess();
        record(userId, loginIdHash, client, LoginResult.SUCCESS, now);
        return tokenService.issue(account.get(), client);
    }

    private void record(Long userId, String loginIdHash, ClientInfo client, LoginResult result, LocalDateTime now) {
        loginAttemptRepository.save(LoginAttempt.of(userId, loginIdHash, client.ipAddress(), result, now));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 계정이 없을 때도 해시 비교에 같은 시간이 들도록 쓰는 값. 처음 필요할 때 한 번 만든다. */
    private volatile String dummyHash;

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
            dummyHash = hash;
        }
        return hash;
    }
}

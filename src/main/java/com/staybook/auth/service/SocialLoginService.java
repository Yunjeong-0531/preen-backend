package com.staybook.auth.service;

import com.staybook.auth.domain.SocialProvider;
import com.staybook.auth.domain.UserSocialAccount;
import com.staybook.auth.dto.AuthResponses;
import com.staybook.auth.dto.ClientInfo;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.auth.oauth.OAuthClient;
import com.staybook.auth.oauth.OAuthClient.OAuthUserInfo;
import com.staybook.auth.oauth.OAuthStateStore;
import com.staybook.auth.repository.UserSocialAccountRepository;
import com.staybook.common.crypto.HashUtils;
import com.staybook.common.exception.BaseException;
import com.staybook.user.dto.UserAccount;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String DEFAULT_NAME = "회원";

    private final List<OAuthClient> clients;
    private final OAuthStateStore stateStore;
    private final UserSocialAccountRepository socialAccountRepository;
    private final UserService userService;
    private final TokenService tokenService;
    private final Clock clock;

    /** state를 만들어 Redis에 저장하고, 사용자를 보낼 제공자 로그인 주소를 돌려준다. */
    public String authorizationUrl(SocialProvider provider) {
        OAuthClient client = client(provider);
        String state = HashUtils.randomToken(24);
        stateStore.save(state, new OAuthStateStore.StateInfo(provider, client.redirectUri()));
        return client.authorizationUrl(state);
    }

    /**
     * 소셜 로그인. 처음이면 회원·개인정보·소셜 연결을 한 트랜잭션으로 만든다(비밀번호 행은 만들지 않는다).
     * 같은 이메일의 기존 회원이 있어도 자동으로 합치지 않고 에러를 돌려준다.
     */
    @Transactional
    public AuthResponses.Token login(SocialProvider provider, String code, String state, ClientInfo client) {
        OAuthUserInfo info = fetchUser(provider, code, state);

        Optional<UserSocialAccount> linked =
                socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
        UserAccount account;
        if (linked.isPresent()) {
            account = userService.findAccount(linked.get().getUserId())
                    .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_CREDENTIALS));
        } else {
            if (info.email() != null && userService.existsByEmail(info.email())) {
                throw new BaseException(AuthErrorCode.SOCIAL_EMAIL_ALREADY_REGISTERED);
            }
            String name = info.name() == null || info.name().isBlank() ? DEFAULT_NAME : info.name();
            Long userId = userService.registerSocial(name, info.email());
            socialAccountRepository.save(
                    UserSocialAccount.create(userId, provider, info.providerUserId(), LocalDateTime.now(clock)));
            account = userService.findAccount(userId)
                    .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_CREDENTIALS));
        }
        return tokenService.issue(account, client);
    }

    /** 이미 로그인한 회원이 소셜 계정을 자기 계정에 연결한다. */
    @Transactional
    public void link(Long userId, SocialProvider provider, String code, String state) {
        OAuthUserInfo info = fetchUser(provider, code, state);
        Optional<UserSocialAccount> linked =
                socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
        if (linked.isPresent()) {
            if (linked.get().getUserId().equals(userId)) {
                return;
            }
            throw new BaseException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        socialAccountRepository.save(
                UserSocialAccount.create(userId, provider, info.providerUserId(), LocalDateTime.now(clock)));
    }

    private OAuthUserInfo fetchUser(SocialProvider provider, String code, String state) {
        OAuthClient client = client(provider);
        stateStore.consume(state)
                .filter(saved -> saved.provider() == provider)
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_OAUTH_STATE));
        return client.fetchUser(code, state);
    }

    private OAuthClient client(SocialProvider provider) {
        OAuthClient client = clients.stream()
                .filter(c -> c.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BaseException(AuthErrorCode.UNSUPPORTED_PROVIDER));
        if (!client.isConfigured()) {
            throw new BaseException(AuthErrorCode.OAUTH_NOT_CONFIGURED);
        }
        return client;
    }
}

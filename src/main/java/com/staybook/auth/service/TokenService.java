package com.staybook.auth.service;

import com.staybook.auth.dto.AuthResponses;
import com.staybook.auth.dto.ClientInfo;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.common.crypto.HashUtils;
import com.staybook.common.exception.BaseException;
import com.staybook.common.security.JwtProvider;
import com.staybook.user.dto.UserAccount;
import com.staybook.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserService userService;

    /** 액세스·리프레시 토큰을 새로 발급하고 리프레시 토큰의 해시를 Redis에 저장한다. */
    public AuthResponses.Token issue(UserAccount account, ClientInfo client) {
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createRefreshToken(account.userId(), tokenId);
        refreshTokenStore.save(account.userId(), tokenId,
                new RefreshTokenStore.Entry(HashUtils.sha256Hex(refreshToken), client.userAgent(), client.ipAddress()),
                jwtProvider.refreshTokenTtl());
        return new AuthResponses.Token(
                jwtProvider.createAccessToken(account.userId(), account.role()),
                refreshToken,
                jwtProvider.accessTokenTtl().toSeconds(),
                account.signupCompleted());
    }

    /** 재발급. 기존 키를 지우고 새로 발급하므로 한 번 쓴 리프레시 토큰은 다시 쓸 수 없다. */
    public AuthResponses.Token refresh(String refreshToken, ClientInfo client) {
        JwtProvider.RefreshTokenClaims claims = jwtProvider.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        RefreshTokenStore.Entry entry = refreshTokenStore.find(claims.userId(), claims.tokenId())
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (!HashUtils.matches(refreshToken, entry.tokenHash())) {
            throw new BaseException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        refreshTokenStore.delete(claims.userId(), claims.tokenId());

        // 역할이 바뀌었거나 탈퇴했을 수 있으므로 회원을 다시 읽는다
        UserAccount account = userService.findAccount(claims.userId())
                .orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        return issue(account, client);
    }

    /** 로그아웃. 이미 만료됐거나 없는 토큰이어도 성공으로 본다. */
    public void logout(String refreshToken) {
        jwtProvider.parseRefreshToken(refreshToken)
                .ifPresent(claims -> refreshTokenStore.delete(claims.userId(), claims.tokenId()));
    }
}

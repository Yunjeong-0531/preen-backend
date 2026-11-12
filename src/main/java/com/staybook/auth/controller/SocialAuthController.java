package com.staybook.auth.controller;

import com.staybook.auth.domain.SocialProvider;
import com.staybook.auth.dto.AuthRequests;
import com.staybook.auth.dto.AuthResponses;
import com.staybook.auth.dto.ClientInfo;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.auth.service.SocialLoginService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.exception.CommonErrorCode;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인. provider는 kakao, naver.
 * 흐름: GET으로 로그인 주소를 받아 이동 → 제공자가 프론트로 code, state를 돌려줌 → POST로 로그인.
 */
@RestController
@RequestMapping("/api/auth/social/{provider}")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialLoginService socialLoginService;

    @GetMapping
    public ApiResponse<AuthResponses.AuthorizationUrl> authorizationUrl(@PathVariable String provider) {
        return ApiResponse.ok(new AuthResponses.AuthorizationUrl(
                socialLoginService.authorizationUrl(parse(provider))));
    }

    @PostMapping
    public ApiResponse<AuthResponses.Token> login(@PathVariable String provider,
                                                  @Valid @RequestBody AuthRequests.SocialCallback request,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok(socialLoginService.login(
                parse(provider), request.code(), request.state(), ClientInfo.from(httpRequest)));
    }

    /** 로그인한 회원이 소셜 계정을 자기 계정에 연결한다. */
    @PostMapping("/link")
    public ApiResponse<Void> link(@PathVariable String provider,
                                  @AuthenticationPrincipal AuthUser authUser,
                                  @Valid @RequestBody AuthRequests.SocialCallback request) {
        if (authUser == null) {
            throw new BaseException(CommonErrorCode.UNAUTHORIZED);
        }
        socialLoginService.link(authUser.userId(), parse(provider), request.code(), request.state());
        return ApiResponse.ok();
    }

    private SocialProvider parse(String provider) {
        return SocialProvider.from(provider)
                .orElseThrow(() -> new BaseException(AuthErrorCode.UNSUPPORTED_PROVIDER));
    }
}

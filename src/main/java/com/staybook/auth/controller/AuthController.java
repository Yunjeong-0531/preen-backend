package com.staybook.auth.controller;

import com.staybook.auth.dto.AuthRequests;
import com.staybook.auth.dto.AuthResponses;
import com.staybook.auth.dto.ClientInfo;
import com.staybook.auth.service.AuthService;
import com.staybook.auth.service.EmailVerificationService;
import com.staybook.auth.service.PasswordResetService;
import com.staybook.auth.service.TokenService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.exception.CommonErrorCode;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final UserService userService;

    @PostMapping("/signup")
    public ApiResponse<AuthResponses.Signup> signup(@Valid @RequestBody AuthRequests.Signup request) {
        return ApiResponse.ok(new AuthResponses.Signup(authService.signup(request)));
    }

    @GetMapping("/check-email")
    public ApiResponse<AuthResponses.CheckEmail> checkEmail(@RequestParam @NotBlank @Email String email) {
        return ApiResponse.ok(new AuthResponses.CheckEmail(authService.isEmailAvailable(email)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponses.Token> login(@Valid @RequestBody AuthRequests.Login request,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponses.Token> refresh(@Valid @RequestBody AuthRequests.RefreshToken request,
                                                    HttpServletRequest httpRequest) {
        return ApiResponse.ok(tokenService.refresh(request.refreshToken(), ClientInfo.from(httpRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AuthRequests.RefreshToken request) {
        tokenService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    /** 인증코드 발송. 다시 호출하면 새 코드를 보내고 이전 코드는 쓸 수 없게 된다. */
    @PostMapping("/email/resend")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody AuthRequests.EmailOnly request) {
        emailVerificationService.send(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/email/verify")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody AuthRequests.EmailVerify request) {
        emailVerificationService.verify(request.email(), request.code());
        return ApiResponse.ok();
    }

    /** 가입 여부와 관계없이 같은 응답을 준다. */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody AuthRequests.EmailOnly request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthRequests.PasswordReset request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ApiResponse.ok();
    }

    /** 소셜 최초 로그인 뒤 생년월일과 약관 동의를 입력해 가입을 끝낸다. 로그인이 필요하다. */
    @PostMapping("/signup/complete")
    public ApiResponse<Void> completeSignup(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody AuthRequests.CompleteSignup request) {
        if (authUser == null) {
            // /api/auth/** 는 토큰 없이 열려 있으므로 여기서 직접 확인한다
            throw new BaseException(CommonErrorCode.UNAUTHORIZED);
        }
        userService.completeSignup(authUser.userId(), request.birthDate(), request.agreedTermIds());
        return ApiResponse.ok();
    }
}

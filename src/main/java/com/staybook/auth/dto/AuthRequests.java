package com.staybook.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** auth 요청 본문 모음. */
public final class AuthRequests {

    /** 영문과 숫자를 포함한 8~64자. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~64자여야 합니다.";

    private AuthRequests() {
    }

    public record Signup(
            @NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,
            @Pattern(regexp = "^[0-9\\-+ ]{9,20}$", message = "전화번호 형식이 올바르지 않습니다.") String phone,
            @NotNull(message = "생년월일을 입력해 주세요.") @Past(message = "생년월일이 올바르지 않습니다.") LocalDate birthDate,
            @NotNull(message = "약관 동의 내역이 필요합니다.") List<Long> agreedTermIds
    ) {
    }

    public record Login(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 64) String password
    ) {
    }

    public record RefreshToken(@NotBlank String refreshToken) {
    }

    public record EmailOnly(@NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 255) String email) {
    }

    public record EmailVerify(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "인증코드는 숫자 6자리입니다.") String code
    ) {
    }

    public record PasswordReset(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String newPassword
    ) {
    }

    /** 소셜 최초 로그인 뒤 추가 입력. */
    public record CompleteSignup(
            @NotNull(message = "생년월일을 입력해 주세요.") @Past(message = "생년월일이 올바르지 않습니다.") LocalDate birthDate,
            @NotNull(message = "약관 동의 내역이 필요합니다.") List<Long> agreedTermIds
    ) {
    }

    /** 제공자가 리다이렉트로 돌려준 code와 state. */
    public record SocialCallback(@NotBlank String code, @NotBlank String state) {
    }
}

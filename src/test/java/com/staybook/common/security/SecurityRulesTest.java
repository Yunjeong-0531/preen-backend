package com.staybook.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.staybook.common.config.ClockConfig;
import com.staybook.common.config.CorsConfig;
import com.staybook.common.exception.BaseException;
import com.staybook.common.exception.CommonErrorCode;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.storage.LocalDiskStorageClient;
import com.staybook.common.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요구사항 4장 "URL과 역할" 표의 접근 규칙과, 실패 응답이 ApiResponse 규격인지 확인한다.
 */
@WebMvcTest(controllers = SecurityRulesTest.ProbeController.class)
@Import({
        SecurityRulesTest.ProbeController.class,
        SecurityConfig.class,
        JwtProvider.class,
        SecurityErrorResponder.class,
        ClockConfig.class,
        CorsConfig.class,
        LocalDiskStorageClient.class
})
// 슬라이스 테스트에서는 @ConfigurationPropertiesScan이 적용되지 않아 직접 등록한다.
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class})
class SecurityRulesTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UserRole role) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(7L, role));
    }

    @Test
    void 인증_약관_숙소조회_웹훅은_토큰_없이_접근한다() throws Exception {
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/api/terms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/accommodations/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/accommodations/1/reviews")).andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/webhook")).andExpect(status().isOk());
    }

    @Test
    void 그_외_경로는_토큰이_없으면_401이고_응답은_공통_규격이다() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.getCode()));
        mockMvc.perform(post("/api/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/host/reservations")).andExpect(status().isUnauthorized());
    }

    @Test
    void 유효하지_않은_토큰은_로그인하지_않은_것으로_본다() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer broken.token.value"))
                .andExpect(status().isUnauthorized());
        // 리프레시 토큰을 액세스 토큰 자리에 넣어도 안 된다
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createRefreshToken(7L, "tid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인한_사용자는_컨트롤러에서_AuthUser로_받는다() throws Exception {
        mockMvc.perform(as(get("/api/users/me"), UserRole.GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.role").value("GUEST"));
    }

    @Test
    void 호스트_경로는_HOST만_접근한다() throws Exception {
        mockMvc.perform(as(get("/api/host/reservations"), UserRole.GUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.FORBIDDEN.getCode()));
        mockMvc.perform(as(get("/api/host/reservations"), UserRole.ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/host/reservations"), UserRole.HOST)).andExpect(status().isOk());
    }

    @Test
    void 사업자_등록_경로는_호스트_경로가_아니라서_GUEST도_접근한다() throws Exception {
        mockMvc.perform(as(post("/api/host-businesses"), UserRole.GUEST)).andExpect(status().isOk());
        mockMvc.perform(post("/api/host-businesses")).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_경로는_ADMIN만_접근한다() throws Exception {
        mockMvc.perform(as(get("/api/admin/ping"), UserRole.HOST)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/ping"), UserRole.ADMIN)).andExpect(status().isOk());
    }

    @Test
    void 숙소_경로라도_조회가_아니면_로그인이_필요하다() throws Exception {
        mockMvc.perform(post("/api/accommodations/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void 예외는_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/api/auth/fail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.NOT_FOUND.getMessage()));
        mockMvc.perform(get("/api/auth/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_ERROR.getMessage()));
    }

    @Test
    void 입력_검증_실패는_400과_필드별_메시지로_응답한다() throws Exception {
        mockMvc.perform(post("/api/auth/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.data.name").exists());
        mockMvc.perform(post("/api/auth/validate").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.getCode()));
    }

    @Test
    void 없는_경로와_지원하지_않는_메서드() throws Exception {
        mockMvc.perform(get("/api/auth/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.METHOD_NOT_ALLOWED.getCode()));
    }

    @RestController
    static class ProbeController {

        @PostMapping("/api/auth/login")
        ApiResponse<Void> login() {
            return ApiResponse.ok();
        }

        @GetMapping({"/api/terms", "/api/accommodations/1", "/api/accommodations/1/reviews",
                "/api/host/reservations", "/api/admin/ping"})
        ApiResponse<Void> read() {
            return ApiResponse.ok();
        }

        @PostMapping({"/api/payments/webhook", "/api/reviews", "/api/host-businesses", "/api/accommodations/1"})
        ApiResponse<Void> write() {
            return ApiResponse.ok();
        }

        @GetMapping("/api/users/me")
        ApiResponse<AuthUser> me(@AuthenticationPrincipal AuthUser user) {
            return ApiResponse.ok(user);
        }

        @GetMapping("/api/auth/fail")
        ApiResponse<Void> fail() {
            throw new BaseException(CommonErrorCode.NOT_FOUND);
        }

        @GetMapping("/api/auth/crash")
        ApiResponse<Void> crash() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }

        @PostMapping("/api/auth/validate")
        ApiResponse<Void> validate(@Valid @RequestBody ProbeRequest request) {
            return ApiResponse.ok();
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }
}

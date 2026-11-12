package com.staybook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.auth.domain.SocialProvider;
import com.staybook.auth.oauth.KakaoOAuthClient;
import com.staybook.auth.oauth.OAuthClient.OAuthUserInfo;
import com.staybook.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/** 소셜 로그인. 카카오 서버 호출만 가짜로 바꾸고 나머지는 실제 MySQL·Redis를 쓴다. */
// PC 환경 변수에 네이버 키가 있어도 이 테스트에서는 "키 없음" 상태로 고정한다.
@TestPropertySource(properties = "staybook.oauth.naver.client-id=")
class SocialLoginTest extends IntegrationTestSupport {

    @MockBean
    KakaoOAuthClient kakao;

    @BeforeEach
    void stubKakao() {
        when(kakao.provider()).thenReturn(SocialProvider.KAKAO);
        when(kakao.isConfigured()).thenReturn(true);
        when(kakao.redirectUri()).thenReturn("http://localhost:3000/oauth/kakao/callback");
        when(kakao.authorizationUrl(anyString()))
                .thenAnswer(inv -> "https://kauth.test/authorize?state=" + inv.getArgument(0));
    }

    /** 카카오가 code에 대해 돌려줄 회원을 정해 두고 그 code를 돌려준다. */
    private String kakaoUser(String providerUserId, String email) {
        String code = "code-" + UUID.randomUUID();
        when(kakao.fetchUser(eq(code), anyString())).thenReturn(new OAuthUserInfo(providerUserId, email, "카카오회원"));
        return code;
    }

    private String newState() throws Exception {
        String url = data(mockMvc.perform(get("/api/auth/social/kakao")).andExpect(status().isOk()))
                .get("authorizationUrl").asText();
        return url.substring(url.indexOf("state=") + "state=".length());
    }

    private JsonNode socialLogin(String code) throws Exception {
        return data(postJson("/api/auth/social/kakao", body("code", code, "state", newState()))
                .andExpect(status().isOk()));
    }

    private long myId(String accessToken) throws Exception {
        return data(getWith("/api/users/me", accessToken).andExpect(status().isOk())).get("id").asLong();
    }

    @Test
    void 최초_소셜_로그인은_비밀번호_행_없이_가입_미완료_회원을_만든다() throws Exception {
        String providerUserId = "kakao-" + UUID.randomUUID();
        String email = uniqueEmail();

        JsonNode token = socialLogin(kakaoUser(providerUserId, email));

        assertThat(token.get("signupCompleted").asBoolean()).isFalse();
        long userId = myId(token.get("accessToken").asText());
        assertThat(jdbc.queryForObject("select count(*) from user_credentials where user_id = ?",
                Integer.class, userId)).isZero();
        Map<String, Object> info = jdbc.queryForMap(
                "select name, email, birth_date from user_personal_info where user_id = ?", userId);
        assertThat(info.get("name")).isEqualTo("카카오회원");
        assertThat((String) info.get("email")).startsWith("v1:");
        assertThat(info.get("birth_date")).isNull();
        assertThat(jdbc.queryForMap("select provider, provider_user_id from user_social_accounts where user_id = ?",
                userId)).containsEntry("provider", "KAKAO").containsEntry("provider_user_id", providerUserId);
        assertThat(jdbc.queryForObject("select count(*) from user_term_agreements where user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void 생년월일과_약관_동의를_입력하면_가입이_완료되고_다시_로그인하면_같은_회원이다() throws Exception {
        String providerUserId = "kakao-" + UUID.randomUUID();
        JsonNode first = socialLogin(kakaoUser(providerUserId, null));
        String accessToken = first.get("accessToken").asText();
        long userId = myId(accessToken);
        Map<String, Object> complete = body("birthDate", LocalDate.now().minusYears(25).toString(),
                "agreedTermIds", requiredTermIds());

        postJson("/api/auth/signup/complete", complete).andExpect(status().isUnauthorized());
        postJson("/api/auth/signup/complete",
                body("birthDate", LocalDate.now().minusYears(25).toString(), "agreedTermIds", java.util.List.of()),
                accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS-001"));
        postJson("/api/auth/signup/complete",
                body("birthDate", LocalDate.now().minusYears(10).toString(), "agreedTermIds", requiredTermIds()),
                accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER-003"));
        postJson("/api/auth/signup/complete", complete, accessToken).andExpect(status().isOk());
        postJson("/api/auth/signup/complete", complete, accessToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER-005"));

        JsonNode second = socialLogin(kakaoUser(providerUserId, null));
        assertThat(second.get("signupCompleted").asBoolean()).isTrue();
        assertThat(myId(second.get("accessToken").asText())).isEqualTo(userId);
        assertThat(jdbc.queryForObject("select count(*) from user_social_accounts where provider_user_id = ?",
                Integer.class, providerUserId)).isEqualTo(1);
    }

    @Test
    void 소셜_이메일이_기존_회원과_같으면_합치지_않고_에러를_준다() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        String providerUserId = "kakao-" + UUID.randomUUID();

        postJson("/api/auth/social/kakao", body("code", kakaoUser(providerUserId, email), "state", newState()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-012"));

        assertThat(jdbc.queryForObject("select count(*) from user_social_accounts where provider_user_id = ?",
                Integer.class, providerUserId)).isZero();
    }

    @Test
    void 기존_계정으로_로그인한_뒤_연결하면_소셜_로그인이_그_계정으로_된다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        String accessToken = login(email, PASSWORD).get("accessToken").asText();
        String providerUserId = "kakao-" + UUID.randomUUID();

        postJson("/api/auth/social/kakao/link", body("code", kakaoUser(providerUserId, email), "state", newState()))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/social/kakao/link", body("code", kakaoUser(providerUserId, email), "state", newState()),
                accessToken).andExpect(status().isOk());

        JsonNode token = socialLogin(kakaoUser(providerUserId, email));
        assertThat(myId(token.get("accessToken").asText())).isEqualTo(userId);

        // 다른 회원에게 이미 연결된 소셜 계정은 연결할 수 없다
        String otherEmail = uniqueEmail();
        signUp(otherEmail);
        String otherToken = login(otherEmail, PASSWORD).get("accessToken").asText();
        postJson("/api/auth/social/kakao/link", body("code", kakaoUser(providerUserId, null), "state", newState()),
                otherToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-013"));
    }

    @Test
    void state는_Redis에_5분_TTL로_저장되고_한_번만_쓸_수_있다() throws Exception {
        String state = newState();
        assertThat(redis.getExpire("oauth:state:" + state)).isBetween(240L, 300L);
        assertThat(redis.opsForValue().get("oauth:state:" + state)).contains("KAKAO").contains("redirectUri");

        postJson("/api/auth/social/kakao",
                body("code", kakaoUser("kakao-" + UUID.randomUUID(), null), "state", state))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("oauth:state:" + state)).isFalse();

        postJson("/api/auth/social/kakao",
                body("code", kakaoUser("kakao-" + UUID.randomUUID(), null), "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-010"));
        postJson("/api/auth/social/kakao", body("code", "any", "state", "made-up-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-010"));
    }

    @Test
    void 키가_없는_제공자와_지원하지_않는_제공자() throws Exception {
        mockMvc.perform(get("/api/auth/social/naver"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH-009"));
        mockMvc.perform(get("/api/auth/social/google"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-008"));
    }
}

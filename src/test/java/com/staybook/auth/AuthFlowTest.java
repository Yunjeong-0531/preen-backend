package com.staybook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.common.crypto.LookupHasher;
import com.staybook.support.IntegrationTestSupport;
import com.staybook.user.service.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2단계(user, auth, terms) 통합 테스트. 실제 MySQL·Redis를 쓴다. */
class AuthFlowTest extends IntegrationTestSupport {

    @Autowired
    LookupHasher lookupHasher;
    @Autowired
    UserService userService;

    // ---------- 가입·로그인 ----------

    @Test
    void 이메일_인증_후_가입하고_로그인해서_내_정보를_본다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);

        JsonNode token = login(email, PASSWORD);
        assertThat(token.get("signupCompleted").asBoolean()).isTrue();
        assertThat(token.get("accessTokenExpiresIn").asLong()).isEqualTo(1800);

        getWith("/api/users/me", token.get("accessToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("GUEST"))
                .andExpect(jsonPath("$.data.signupCompleted").value(true));
    }

    @Test
    void 개인정보는_암호화하고_비밀번호와_인증코드는_해시로만_저장한다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        login(email, PASSWORD);

        Map<String, Object> info = jdbc.queryForMap(
                "select email, email_hash, phone, phone_hash from user_personal_info where user_id = ?", userId);
        assertThat((String) info.get("email")).startsWith("v1:").doesNotContain(email);
        assertThat((String) info.get("phone")).startsWith("v1:").doesNotContain("1234");
        assertThat(info.get("email_hash")).isEqualTo(lookupHasher.hashEmail(email));
        assertThat(info.get("phone_hash")).isEqualTo(lookupHasher.hashPhone("01012345678"));

        String passwordHash = jdbc.queryForObject(
                "select password_hash from user_credentials where user_id = ?", String.class, userId);
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);

        String codeHash = jdbc.queryForObject(
                "select code_hash from verification_codes where target_hash = ? order by id desc limit 1",
                String.class, lookupHasher.hashEmail(email));
        assertThat(codeHash).hasSize(64).isNotEqualTo(mail.lastCode(email));

        // 리프레시 토큰도 Redis에 해시로만 들어간다
        String key = redis.keys("refresh:" + userId + ":*").iterator().next();
        assertThat(redis.opsForValue().get(key)).contains("tokenHash").doesNotContain("eyJ");
        assertThat(redis.getExpire(key)).isBetween(14L * 24 * 3600 - 60, 14L * 24 * 3600);
    }

    @Test
    void 이메일_인증을_하지_않으면_가입할_수_없다() throws Exception {
        String email = uniqueEmail();

        postJson("/api/auth/signup", signupBody(email, LocalDate.now().minusYears(30), requiredTermIds()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-004"));
    }

    @Test
    void 이미_가입된_이메일로는_가입할_수_없고_중복_확인에_나온다() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(get("/api/auth/check-email").param("email", email))
                .andExpect(jsonPath("$.data.available").value(true));
        signUp(email);

        mockMvc.perform(get("/api/auth/check-email").param("email", email.toUpperCase()))
                .andExpect(jsonPath("$.data.available").value(false));
        verifyEmail(email);
        postJson("/api/auth/signup", signupBody(email, LocalDate.now().minusYears(30), requiredTermIds()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER-002"));
    }

    @Test
    void 입력_형식이_틀리면_400이다() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> weakPassword = signupBody(email, LocalDate.now().minusYears(30), List.of());
        weakPassword.put("password", "short");

        postJson("/api/auth/signup", weakPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"))
                .andExpect(jsonPath("$.data.password").exists());
        mockMvc.perform(get("/api/auth/check-email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 약관 ----------

    @Test
    void 현재_유효한_약관_목록은_토큰_없이_조회한다() throws Exception {
        JsonNode terms = data(mockMvc.perform(get("/api/terms")).andExpect(status().isOk()));

        List<String> types = new ArrayList<>();
        terms.forEach(t -> types.add(t.get("type").asText() + ":" + t.get("required").asBoolean()));
        assertThat(types).containsExactlyInAnyOrder("SERVICE:true", "PRIVACY:true", "MARKETING:false");
    }

    @Test
    void 필수_약관에_동의하지_않으면_가입을_거부한다() throws Exception {
        String email = uniqueEmail();
        verifyEmail(email);
        List<Long> onlyOne = requiredTermIds().subList(0, 1);

        postJson("/api/auth/signup", signupBody(email, LocalDate.now().minusYears(30), onlyOne))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS-001"));
        // 가입 전체가 롤백돼 회원이 남지 않는다
        assertThat(jdbc.queryForObject("select count(*) from user_personal_info where email_hash = ?",
                Integer.class, lookupHasher.hashEmail(email))).isZero();

        postJson("/api/auth/signup", signupBody(email, LocalDate.now().minusYears(30), List.of(999_999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS-002"));
    }

    @Test
    void 약관_동의는_버전별로_남고_선택_약관_거절도_기록된다() throws Exception {
        long userId = signUp(uniqueEmail());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select t.type, a.is_agreed from user_term_agreements a join terms t on t.id = a.term_id "
                        + "where a.user_id = ? order by t.id", userId);
        assertThat(rows).extracting(r -> r.get("type") + ":" + r.get("is_agreed"))
                .containsExactly("SERVICE:true", "PRIVACY:true", "MARKETING:false");
    }

    // ---------- 미성년자 ----------

    @Test
    void 만_14세_미만은_가입할_수_없고_만_14세는_가입할_수_있다() throws Exception {
        String child = uniqueEmail();
        verifyEmail(child);
        postJson("/api/auth/signup",
                signupBody(child, LocalDate.now().minusYears(14).plusDays(1), requiredTermIds()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER-003"));

        long teenId = signUp(uniqueEmail(), LocalDate.now().minusYears(14));
        assertThat(userService.isAdult(teenId)).isFalse();

        long adultId = signUp(uniqueEmail(), LocalDate.now().minusYears(19));
        assertThat(userService.isAdult(adultId)).isTrue();
        long almostAdultId = signUp(uniqueEmail(), LocalDate.now().minusYears(19).plusDays(1));
        assertThat(userService.isAdult(almostAdultId)).isFalse();
    }

    // ---------- 로그인 실패·잠금 ----------

    @Test
    void 비밀번호를_5번_틀리면_잠기고_잠금이_풀리면_다시_로그인된다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);

        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", body("email", email, "password", "wrong-pass1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-001"));
        }
        // 잠긴 뒤에는 맞는 비밀번호도 거부한다
        postJson("/api/auth/login", body("email", email, "password", PASSWORD))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH-002"));

        Map<String, Object> cred = jdbc.queryForMap(
                "select failed_login_count, locked_until from user_credentials where user_id = ?", userId);
        assertThat(((Number) cred.get("failed_login_count")).intValue()).isEqualTo(5);
        assertThat(cred.get("locked_until")).isNotNull();
        assertThat(jdbc.queryForList("select result from login_attempts where user_id = ? order by id",
                String.class, userId))
                .containsExactly("WRONG_PASSWORD", "WRONG_PASSWORD", "WRONG_PASSWORD", "WRONG_PASSWORD",
                        "WRONG_PASSWORD", "LOCKED");

        // 잠금 시간이 지난 것으로 만든다
        jdbc.update("update user_credentials set locked_until = now(6) - interval 1 second where user_id = ?", userId);
        login(email, PASSWORD);
        assertThat(jdbc.queryForObject("select failed_login_count from user_credentials where user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void 실패_횟수는_로그인에_성공하면_초기화된다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        for (int i = 0; i < 4; i++) {
            postJson("/api/auth/login", body("email", email, "password", "wrong-pass1"))
                    .andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD);

        assertThat(jdbc.queryForObject("select failed_login_count from user_credentials where user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void 없는_계정으로_로그인을_시도해도_같은_에러이고_기록은_user_id_NULL로_남는다() throws Exception {
        String email = uniqueEmail();

        postJson("/api/auth/login", body("email", email, "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        Map<String, Object> attempt = jdbc.queryForMap(
                "select user_id, result, ip_address from login_attempts where login_id_hash = ?",
                lookupHasher.hashEmail(email));
        assertThat(attempt.get("user_id")).isNull();
        assertThat(attempt.get("result")).isEqualTo("NO_ACCOUNT");
        assertThat(attempt.get("ip_address")).isNotNull();
    }

    // ---------- 인증코드 ----------

    @Test
    void 인증코드를_5번_틀리면_맞는_코드도_무효가_되고_재발송하면_다시_인증할_수_있다() throws Exception {
        String email = uniqueEmail();
        postJson("/api/auth/email/resend", body("email", email)).andExpect(status().isOk());
        String code = mail.lastCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/email/verify", body("email", email, "code", wrong))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH-005"));
        }
        postJson("/api/auth/email/verify", body("email", email, "code", code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-006"));

        postJson("/api/auth/email/resend", body("email", email)).andExpect(status().isOk());
        postJson("/api/auth/email/verify", body("email", email, "code", mail.lastCode(email)))
                .andExpect(status().isOk());
    }

    @Test
    void 만료된_인증코드와_재발송_전의_코드는_쓸_수_없다() throws Exception {
        String email = uniqueEmail();
        postJson("/api/auth/email/resend", body("email", email)).andExpect(status().isOk());
        String first = mail.lastCode(email);
        postJson("/api/auth/email/resend", body("email", email)).andExpect(status().isOk());
        String second = mail.lastCode(email);

        if (!first.equals(second)) {
            postJson("/api/auth/email/verify", body("email", email, "code", first))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("update verification_codes set expires_at = now(6) - interval 1 second where target_hash = ?",
                lookupHasher.hashEmail(email));
        postJson("/api/auth/email/verify", body("email", email, "code", second))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    // ---------- 토큰 ----------

    @Test
    void 재발급하면_기존_리프레시_토큰은_못_쓰고_로그아웃하면_키가_지워진다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        String oldRefresh = login(email, PASSWORD).get("refreshToken").asText();
        assertThat(refreshKeyCount(userId)).isEqualTo(1);

        JsonNode renewed = data(postJson("/api/auth/refresh", body("refreshToken", oldRefresh))
                .andExpect(status().isOk()));
        String newRefresh = renewed.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(refreshKeyCount(userId)).isEqualTo(1);
        getWith("/api/users/me", renewed.get("accessToken").asText()).andExpect(status().isOk());

        postJson("/api/auth/refresh", body("refreshToken", oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
        // 액세스 토큰은 리프레시 토큰 자리에 쓸 수 없다
        postJson("/api/auth/refresh", body("refreshToken", renewed.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());

        postJson("/api/auth/logout", body("refreshToken", newRefresh)).andExpect(status().isOk());
        assertThat(refreshKeyCount(userId)).isZero();
        postJson("/api/auth/refresh", body("refreshToken", newRefresh)).andExpect(status().isUnauthorized());
        // 이미 로그아웃한 토큰으로 다시 로그아웃해도 성공이다
        postJson("/api/auth/logout", body("refreshToken", newRefresh)).andExpect(status().isOk());
    }

    // ---------- 비밀번호 재설정 ----------

    @Test
    void 재설정_링크로_비밀번호를_바꾸면_기존_비밀번호와_리프레시_토큰은_못_쓴다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        String refreshBefore = login(email, PASSWORD).get("refreshToken").asText();
        login(email, PASSWORD);
        assertThat(refreshKeyCount(userId)).isEqualTo(2);

        postJson("/api/auth/password/forgot", body("email", email)).andExpect(status().isOk());
        String link = mail.lastResetLink(email);
        assertThat(link).startsWith("http://localhost:3000/reset-password?token=");
        String token = link.substring(link.indexOf("token=") + "token=".length());
        // DB에는 토큰 원문이 없다
        assertThat(jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = ?",
                String.class, userId)).hasSize(64).isNotEqualTo(token);

        postJson("/api/auth/password/reset", body("token", token, "newPassword", "weak"))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/password/reset", body("token", token, "newPassword", "new-Passw0rd"))
                .andExpect(status().isOk());

        assertThat(refreshKeyCount(userId)).isZero();
        postJson("/api/auth/refresh", body("refreshToken", refreshBefore)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", body("email", email, "password", PASSWORD)).andExpect(status().isUnauthorized());
        login(email, "new-Passw0rd");

        // 한 번 쓴 토큰은 다시 쓸 수 없다
        postJson("/api/auth/password/reset", body("token", token, "newPassword", "another-Passw0rd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
    }

    @Test
    void 가입하지_않은_이메일의_재설정_요청도_같은_응답이고_메일은_가지_않는다() throws Exception {
        String email = uniqueEmail();

        postJson("/api/auth/password/forgot", body("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(mail.lastResetLink(email)).isNull();
    }

    @Test
    void 만료된_재설정_토큰은_쓸_수_없다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        postJson("/api/auth/password/forgot", body("email", email)).andExpect(status().isOk());
        String link = mail.lastResetLink(email);
        String token = link.substring(link.indexOf("token=") + "token=".length());
        jdbc.update("update password_reset_tokens set expires_at = now(6) - interval 1 second where user_id = ?", userId);

        postJson("/api/auth/password/reset", body("token", token, "newPassword", "new-Passw0rd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH-007"));
        login(email, PASSWORD);
    }

    // ---------- 내 정보·탈퇴 ----------

    @Test
    void 내_정보를_수정한다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        String accessToken = login(email, PASSWORD).get("accessToken").asText();

        mockMvc.perform(withToken(patch("/api/users/me"), accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"phone\":\"010-9999-8888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.phone").value("010-9999-8888"))
                .andExpect(jsonPath("$.data.email").value(email));
        assertThat(jdbc.queryForObject("select phone_hash from user_personal_info where user_id = ?",
                String.class, userId)).isEqualTo(lookupHasher.hashPhone("01099998888"));

        // 보내지 않은 항목은 그대로 둔다
        mockMvc.perform(withToken(patch("/api/users/me"), accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"또바꿈\"}"))
                .andExpect(jsonPath("$.data.name").value("또바꿈"))
                .andExpect(jsonPath("$.data.phone").value("010-9999-8888"));
        mockMvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴하면_행은_남고_deleted_at이_채워지며_리프레시_토큰이_전부_지워진다() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        JsonNode first = login(email, PASSWORD);
        login(email, PASSWORD);
        assertThat(refreshKeyCount(userId)).isEqualTo(2);

        mockMvc.perform(withToken(delete("/api/users/me"), first.get("accessToken").asText()))
                .andExpect(status().isOk());

        Map<String, Object> user = jdbc.queryForMap("select status, deleted_at from users where id = ?", userId);
        assertThat(user.get("status")).isEqualTo("WITHDRAWN");
        assertThat(user.get("deleted_at")).isNotNull();
        assertThat(refreshKeyCount(userId)).isZero();

        postJson("/api/auth/login", body("email", email, "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", body("refreshToken", first.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        // 남아 있는 액세스 토큰으로도 회원 정보는 볼 수 없다
        getWith("/api/users/me", first.get("accessToken").asText()).andExpect(status().isNotFound());
    }
}

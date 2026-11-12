package com.staybook.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staybook.auth.service.AuthMailSender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 통합 테스트 공통. .env의 MySQL·Redis(도커)에 실제로 붙고, Flyway 마이그레이션이 적용된 스키마를 쓴다.
 * 테스트마다 새 이메일을 써서 서로 영향을 주지 않는다. 메일은 보내지 않고 내용을 잡아 둔다.
 */
@SpringBootTest(properties = {
        "staybook.scheduling.enabled=false",          // 스케줄러는 테스트에서 직접 호출한다
        "staybook.pg.provider=fake",                  // 실제 PG 대신 가짜 구현체
        "staybook.pg.webhook-secret=test-webhook-secret"
})
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.TestMailConfig.class)
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "passw0rd-Test";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected CapturingMailSender mail;

    // ---------- 요청 ----------

    protected ResultActions postJson(String path, Object body) throws Exception {
        return postJson(path, body, null);
    }

    protected ResultActions postJson(String path, Object body, String accessToken) throws Exception {
        return mockMvc.perform(withToken(post(path), accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions getWith(String path, String accessToken) throws Exception {
        return mockMvc.perform(withToken(get(path), accessToken));
    }

    protected MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String accessToken) {
        return accessToken == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    /** 응답 본문의 data 부분. */
    protected JsonNode data(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data");
    }

    // ---------- 시나리오 조각 ----------

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.staybook.local";
    }

    protected Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** 현재 유효한 약관 중 필수 약관의 ID. */
    protected List<Long> requiredTermIds() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode term : data(mockMvc.perform(get("/api/terms")).andExpect(status().isOk()))) {
            if (term.get("required").asBoolean()) {
                ids.add(term.get("id").asLong());
            }
        }
        return ids;
    }

    /** 인증코드 발송 → 확인까지. */
    protected void verifyEmail(String email) throws Exception {
        postJson("/api/auth/email/resend", body("email", email)).andExpect(status().isOk());
        postJson("/api/auth/email/verify", body("email", email, "code", mail.lastCode(email)))
                .andExpect(status().isOk());
    }

    protected Map<String, Object> signupBody(String email, LocalDate birthDate, List<Long> agreedTermIds) {
        return body("email", email, "password", PASSWORD, "name", "테스트회원", "phone", "010-1234-5678",
                "birthDate", birthDate.toString(), "agreedTermIds", agreedTermIds);
    }

    /** 이메일 인증 → 가입. 가입한 회원 번호를 돌려준다. */
    protected long signUp(String email, LocalDate birthDate) throws Exception {
        verifyEmail(email);
        return data(postJson("/api/auth/signup", signupBody(email, birthDate, requiredTermIds()))
                .andExpect(status().isOk())).get("userId").asLong();
    }

    /** 성인 회원으로 가입. */
    protected long signUp(String email) throws Exception {
        return signUp(email, LocalDate.now().minusYears(30));
    }

    /** 로그인해서 토큰 응답(data)을 돌려준다. */
    protected JsonNode login(String email, String password) throws Exception {
        return data(postJson("/api/auth/login", body("email", email, "password", password))
                .andExpect(status().isOk()));
    }

    protected int refreshKeyCount(long userId) {
        var keys = redis.keys("refresh:" + userId + ":*");
        return keys == null ? 0 : keys.size();
    }

    // ---------- 호스트·숙소 ----------

    protected ResultActions patchJson(String path, Object body, String accessToken) throws Exception {
        return mockMvc.perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path), accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putJson(String path, Object body, String accessToken) throws Exception {
        return mockMvc.perform(withToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 검증 번호가 맞는 임의의 사업자등록번호 (실제 사업자와 무관한 테스트 값). */
    protected String randomBusinessNo() {
        String firstNine = String.format("%09d",
                java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L));
        return firstNine + com.staybook.host.domain.BusinessRegistrationNo.checkDigit(firstNine);
    }

    public record Member(long userId, String email, String token) {
    }

    /** 성인 회원으로 가입하고 로그인한다. */
    protected Member createMember() throws Exception {
        String email = uniqueEmail();
        long userId = signUp(email);
        return new Member(userId, email, login(email, PASSWORD).get("accessToken").asText());
    }

    /** 가입 → 로그인 → 사업자 등록까지 마친 호스트. token은 HOST 역할이 담긴 새 토큰이다. */
    protected Member createHost() throws Exception {
        Member member = createMember();
        String hostToken = data(postJson("/api/host-businesses",
                body("businessName", "테스트 숙박업", "businessRegistrationNo", randomBusinessNo()), member.token())
                .andExpect(status().isOk())).get("accessToken").asText();
        return new Member(member.userId(), member.email(), hostToken);
    }

    /** 호텔·리조트(카테고리 1)로 등록하는 요청 본문. */
    protected Map<String, Object> accommodationBody(long regionId, String name) {
        return body("regionId", regionId, "categoryId", 1, "name", name, "description", "테스트 숙소 설명",
                "address", "테스트시 테스트구 1-1", "latitude", 37.5665, "longitude", 126.978,
                "checkInTime", "15:00", "checkOutTime", "11:00",
                "imageUrls", List.of("http://localhost:8080/uploads/a.jpg", "http://localhost:8080/uploads/b.jpg"),
                "amenityIds", List.of(1, 2));
    }

    protected Map<String, Object> roomBody(String name, int capacity, int maxCapacity, int extraPersonFee,
                                           int nightlyPrice) {
        return body("name", name, "capacity", capacity, "maxCapacity", maxCapacity,
                "extraPersonFee", extraPersonFee, "nightlyPrice", nightlyPrice,
                "imageUrls", List.of("http://localhost:8080/uploads/room.jpg"));
    }

    /** 체크인 7일 전까지 100%, 3일 전까지 50%, 그 이후 0%. */
    protected Map<String, Object> refundPolicyBody() {
        return body("policyText", "체크인 7일 전까지 전액 환불",
                "rules", List.of(body("daysBefore", 7, "refundRate", 1.0),
                        body("daysBefore", 3, "refundRate", 0.5),
                        body("daysBefore", 0, "refundRate", 0.0)));
    }

    public record Listing(long accommodationId, long roomId) {
    }

    /** 숙소 등록 → 객실(기준 2명, 최대 4명, 추가 1인 2만원, 기본가 10만원) → 환불정책 → 공개. */
    protected Listing createListedAccommodation(Member host, long regionId) throws Exception {
        long accommodationId = data(postJson("/api/host/accommodations",
                accommodationBody(regionId, "테스트 숙소 " + UUID.randomUUID()), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        long roomId = data(postJson("/api/host/accommodations/" + accommodationId + "/rooms",
                roomBody("디럭스", 2, 4, 20000, 100000), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        putJson("/api/host/accommodations/" + accommodationId + "/refund-policy", refundPolicyBody(), host.token())
                .andExpect(status().isOk());
        patchJson("/api/host/accommodations/" + accommodationId, body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isOk());
        return new Listing(accommodationId, roomId);
    }

    // ---------- 재고·예약·결제 ----------

    /** start부터 end까지(양 끝 포함) 수량과 가격을 설정한다. */
    protected void setInventory(Member host, long roomId, java.time.LocalDate start, java.time.LocalDate end,
                                int totalCount, int price) throws Exception {
        putJson("/api/host/rooms/" + roomId + "/inventories",
                body("startDate", start.toString(), "endDate", end.toString(), "totalCount", totalCount,
                        "price", price), host.token())
                .andExpect(status().isOk());
    }

    protected ResultActions reserve(Member member, long roomId, java.time.LocalDate checkIn,
                                    java.time.LocalDate checkOut, int guestCount) throws Exception {
        return postJson("/api/reservations",
                body("roomId", roomId, "checkIn", checkIn.toString(), "checkOut", checkOut.toString(),
                        "guestCount", guestCount), member.token());
    }

    /** 예약을 만들고 응답의 data를 돌려준다. */
    protected JsonNode reserveOk(Member member, long roomId, java.time.LocalDate checkIn,
                                 java.time.LocalDate checkOut, int guestCount) throws Exception {
        return data(reserve(member, roomId, checkIn, checkOut, guestCount).andExpect(status().isOk()));
    }

    /** 결제 준비. 발급된 주문 번호를 돌려준다. */
    protected String preparePayment(Member member, long reservationId) throws Exception {
        return data(postJson("/api/payments/prepare", body("reservationId", reservationId), member.token())
                .andExpect(status().isOk())).get("orderId").asText();
    }

    protected ResultActions confirmPayment(Member member, String orderId, String paymentKey, long amount)
            throws Exception {
        return postJson("/api/payments/confirm",
                body("orderId", orderId, "paymentKey", paymentKey, "amount", amount), member.token());
    }

    /** 결제 준비 → 승인까지 성공시킨다. 사용한 결제 키를 돌려준다. */
    protected String pay(Member member, JsonNode reservation) throws Exception {
        String paymentKey = "pay-" + UUID.randomUUID();
        confirmPayment(member, preparePayment(member, reservation.get("id").asLong()), paymentKey,
                reservation.get("totalPrice").asLong()).andExpect(status().isOk());
        return paymentKey;
    }

    protected String reservationStatus(long reservationId) {
        return jdbc.queryForObject("select status from reservations where id = ?", String.class, reservationId);
    }

    /** 그 객실·날짜의 재고 카운트. 예: "held=1,booked=0" */
    protected String inventoryCounts(long roomId, java.time.LocalDate date) {
        Map<String, Object> row = jdbc.queryForMap(
                "select held_count, booked_count from room_inventories where room_id = ? and stay_date = ?",
                roomId, date);
        return "held=" + row.get("held_count") + ",booked=" + row.get("booked_count");
    }

    // ---------- 메일 ----------

    @TestConfiguration
    public static class TestMailConfig {

        @Bean
        @Primary
        public CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    public static class CapturingMailSender implements AuthMailSender {

        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, String> resetLinks = new ConcurrentHashMap<>();

        @Override
        public void sendVerificationCode(String email, String code) {
            codes.put(email, code);
        }

        @Override
        public void sendPasswordResetLink(String email, String resetLink) {
            resetLinks.put(email, resetLink);
        }

        public String lastCode(String email) {
            return codes.get(email);
        }

        public String lastResetLink(String email) {
            return resetLinks.get(email);
        }
    }
}

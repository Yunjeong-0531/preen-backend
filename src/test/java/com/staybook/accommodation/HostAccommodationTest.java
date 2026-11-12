package com.staybook.accommodation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.host.domain.BusinessRegistrationNo;
import com.staybook.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 3단계(host, accommodation) 통합 테스트. 실제 MySQL·Redis를 쓴다. */
class HostAccommodationTest extends IntegrationTestSupport {

    private static final long SEOUL = 1L;
    private static final long GANGNAM = 101L;
    private static final long JEJU = 17L;
    private static final long SEOGWIPO = 1702L;

    // ---------- 사업자 등록 ----------

    @Test
    void 사업자_등록을_하면_바로_승인되고_HOST가_되며_새_토큰을_받는다() throws Exception {
        Member member = createMember();
        String businessNo = randomBusinessNo();
        // 등록 전에는 GUEST라 호스트 경로에 접근할 수 없다
        getWith("/api/host/business", member.token()).andExpect(status().isForbidden());

        JsonNode registered = data(postJson("/api/host-businesses",
                body("businessName", "바다펜션",
                        "businessRegistrationNo", businessNo.substring(0, 3) + "-" + businessNo.substring(3, 5)
                                + "-" + businessNo.substring(5)),
                member.token()).andExpect(status().isOk()));

        assertThat(registered.path("business").get("approvalStatus").asText()).isEqualTo("APPROVED");
        assertThat(registered.path("business").get("businessRegistrationNo").asText()).isEqualTo(businessNo);
        assertThat(jdbc.queryForObject("select role from users where id = ?", String.class, member.userId()))
                .isEqualTo("HOST");

        String hostToken = registered.get("accessToken").asText();
        getWith("/api/host/business", hostToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("바다펜션"));
        // 예전 토큰에는 GUEST 역할이 들어 있어 여전히 막힌다
        getWith("/api/host/business", member.token()).andExpect(status().isForbidden());
        // 다시 로그인하면 HOST 토큰이 나온다
        getWith("/api/host/business", login(member.email(), PASSWORD).get("accessToken").asText())
                .andExpect(status().isOk());
    }

    @Test
    void 사업자등록번호_형식이_틀리면_거부하고_역할도_바뀌지_않는다() throws Exception {
        Member member = createMember();
        String valid = randomBusinessNo();
        int wrongCheck = (valid.charAt(9) - '0' + 1) % 10;

        for (String invalid : List.of("12345", "abcdefghij", valid.substring(0, 9) + wrongCheck)) {
            postJson("/api/host-businesses", body("businessName", "상호", "businessRegistrationNo", invalid),
                    member.token())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("HOST-001"));
        }
        assertThat(jdbc.queryForObject("select role from users where id = ?", String.class, member.userId()))
                .isEqualTo("GUEST");
        postJson("/api/host-businesses", body("businessName", "상호", "businessRegistrationNo", valid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 검증_번호_계산은_알려진_예시와_맞는다() {
        // 공개된 형식 설명에 쓰이는 예시 번호
        assertThat(BusinessRegistrationNo.normalize("123-45-67891")).contains("1234567891");
        assertThat(BusinessRegistrationNo.normalize("1234567890")).isEmpty();
        assertThat(BusinessRegistrationNo.normalize("123-456-7891")).isEmpty();
        assertThat(BusinessRegistrationNo.normalize(null)).isEmpty();
    }

    @Test
    void 같은_번호나_같은_회원으로는_두_번_등록할_수_없다() throws Exception {
        Member first = createMember();
        Member second = createMember();
        String businessNo = randomBusinessNo();
        postJson("/api/host-businesses", body("businessName", "상호", "businessRegistrationNo", businessNo),
                first.token()).andExpect(status().isOk());

        postJson("/api/host-businesses", body("businessName", "다른 상호", "businessRegistrationNo", businessNo),
                second.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOST-003"));
        postJson("/api/host-businesses",
                body("businessName", "또 다른 상호", "businessRegistrationNo", randomBusinessNo()), first.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOST-002"));
    }

    // ---------- 지역·편의시설 ----------

    @Test
    void 지역과_편의시설_목록은_토큰_없이_조회한다() throws Exception {
        JsonNode regions = data(mockMvc.perform(get("/api/regions")).andExpect(status().isOk()));
        List<String> rows = new ArrayList<>();
        regions.forEach(r -> rows.add(r.get("id").asLong() + ":" + r.get("name").asText() + ":"
                + r.get("level").asInt() + ":" + (r.get("parentId").isNull() ? "-" : r.get("parentId").asText())));
        assertThat(rows).contains("1:서울:1:-", "17:제주:1:-", "101:강남구:2:1", "1702:서귀포시:2:17");

        mockMvc.perform(get("/api/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("와이파이"))
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    // ---------- 숙소 등록·공개 ----------

    @Test
    void GUEST는_숙소를_등록할_수_없다() throws Exception {
        Member guest = createMember();

        postJson("/api/host/accommodations", accommodationBody(SEOUL, "게스트의 숙소"), guest.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
        postJson("/api/host/accommodations", accommodationBody(SEOUL, "비로그인")).andExpect(status().isUnauthorized());
    }

    @Test
    void 숙소는_DRAFT로_만들어지고_객실과_환불정책을_등록해야_공개할_수_있다() throws Exception {
        Member host = createHost();
        JsonNode created = data(postJson("/api/host/accommodations", accommodationBody(GANGNAM, "강남 스테이"),
                host.token()).andExpect(status().isOk()));
        long id = created.get("id").asLong();
        String path = "/api/host/accommodations/" + id;
        assertThat(created.get("listingStatus").asText()).isEqualTo("DRAFT");
        assertThat(created.get("regionName").asText()).isEqualTo("강남구");

        // 작성 중인 숙소는 공개 조회에 나오지 않는다
        mockMvc.perform(get("/api/accommodations/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-001"));
        patchJson(path, body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-007"));

        postJson(path + "/rooms", roomBody("디럭스", 2, 4, 20000, 120000), host.token()).andExpect(status().isOk());
        patchJson(path, body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-007"));
        putJson(path + "/refund-policy", refundPolicyBody(), host.token()).andExpect(status().isOk());
        patchJson(path, body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listingStatus").value("LISTED"));

        mockMvc.perform(get("/api/accommodations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("강남 스테이"))
                .andExpect(jsonPath("$.data.checkInTime").value("15:00:00"))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("http://localhost:8080/uploads/a.jpg"))
                .andExpect(jsonPath("$.data.imageUrls[1]").value("http://localhost:8080/uploads/b.jpg"))
                .andExpect(jsonPath("$.data.amenities[0].name").value("와이파이"))
                .andExpect(jsonPath("$.data.amenities[1].name").value("주차"))
                .andExpect(jsonPath("$.data.rooms[0].name").value("디럭스"))
                .andExpect(jsonPath("$.data.rooms[0].nightlyPrice").value(120000))
                .andExpect(jsonPath("$.data.rooms[0].imageUrls[0]").value("http://localhost:8080/uploads/room.jpg"))
                .andExpect(jsonPath("$.data.refundPolicy.version").value("v1"))
                .andExpect(jsonPath("$.data.refundPolicy.rules[0].daysBefore").value(7))
                .andExpect(jsonPath("$.data.refundPolicy.rules[0].refundRate").value(1.0))
                .andExpect(jsonPath("$.data.refundPolicy.rules[2].daysBefore").value(0));

        // 숨기면 다시 공개 조회에서 빠진다
        patchJson(path, body("listingStatus", "HIDDEN"), host.token()).andExpect(status().isOk());
        mockMvc.perform(get("/api/accommodations/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 숙소를_수정하면_보낸_항목만_바뀌고_이미지와_편의시설은_통째로_교체된다() throws Exception {
        Member host = createHost();
        long id = data(postJson("/api/host/accommodations", accommodationBody(SEOUL, "원래 이름"), host.token())
                .andExpect(status().isOk())).get("id").asLong();

        patchJson("/api/host/accommodations/" + id,
                body("name", "바뀐 이름", "imageUrls", List.of("http://localhost:8080/uploads/c.jpg"),
                        "amenityIds", List.of(3)), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("바뀐 이름"))
                .andExpect(jsonPath("$.data.address").value("테스트시 테스트구 1-1"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("http://localhost:8080/uploads/c.jpg"))
                .andExpect(jsonPath("$.data.amenities.length()").value(1))
                .andExpect(jsonPath("$.data.amenities[0].name").value("조식"));

        patchJson("/api/host/accommodations/" + id, body("regionId", 999_999), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-003"));
        patchJson("/api/host/accommodations/" + id, body("amenityIds", List.of(1, 999_999)), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-004"));
        // 실패한 수정은 롤백돼 편의시설이 그대로다
        assertThat(jdbc.queryForList("select amenity_id from accommodation_amenities where accommodation_id = ?",
                Long.class, id)).containsExactly(3L);
    }

    @Test
    void 다른_호스트의_숙소는_수정도_객실_등록도_환불정책_설정도_할_수_없다() throws Exception {
        Member owner = createHost();
        Member other = createHost();
        Listing listing = createListedAccommodation(owner, SEOUL);
        String path = "/api/host/accommodations/" + listing.accommodationId();

        patchJson(path, body("name", "남의 숙소 바꾸기"), other.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        postJson(path + "/rooms", roomBody("몰래 추가", 2, 2, 0, 1000), other.token())
                .andExpect(status().isForbidden());
        patchJson(path + "/rooms/" + listing.roomId(), body("nightlyPrice", 1), other.token())
                .andExpect(status().isForbidden());
        putJson(path + "/refund-policy", refundPolicyBody(), other.token()).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from rooms where accommodation_id = ?", Integer.class,
                listing.accommodationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refund_policies where accommodation_id = ?",
                Integer.class, listing.accommodationId())).isEqualTo(1);
    }

    // ---------- 객실 ----------

    @Test
    void 객실을_수정하고_판매를_중지하면_공개_상세에서_빠진다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String roomsPath = "/api/host/accommodations/" + listing.accommodationId() + "/rooms";
        long secondRoomId = data(postJson(roomsPath, roomBody("스위트", 2, 6, 30000, 250000), host.token())
                .andExpect(status().isOk())).get("id").asLong();

        patchJson(roomsPath + "/" + listing.roomId(), body("nightlyPrice", 90000), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nightlyPrice").value(90000))
                .andExpect(jsonPath("$.data.name").value("디럭스"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1));
        patchJson(roomsPath + "/" + secondRoomId, body("status", "INACTIVE"), host.token())
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accommodations/" + listing.accommodationId()))
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].nightlyPrice").value(90000));
    }

    @Test
    void 객실_입력_검증() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String roomsPath = "/api/host/accommodations/" + listing.accommodationId() + "/rooms";

        postJson(roomsPath, roomBody("최대가 기준보다 작음", 4, 2, 0, 100000), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-006"));
        postJson(roomsPath, roomBody("가격 0", 2, 2, 0, 0), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        patchJson(roomsPath + "/" + listing.roomId(), body("maxCapacity", 1), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-006"));
        patchJson(roomsPath + "/999999999", body("name", "없는 객실"), host.token())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-005"));
    }

    // ---------- 환불정책 ----------

    @Test
    void 환불정책을_다시_설정하면_새_버전이_생기고_과거_버전은_그대로_남는다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/host/accommodations/" + listing.accommodationId() + "/refund-policy";
        Map<String, Object> firstPolicy = jdbc.queryForMap(
                "select id, version, active from refund_policies where accommodation_id = ?",
                listing.accommodationId());

        putJson(path, body("policyText", "환불 불가",
                "rules", List.of(body("daysBefore", 0, "refundRate", 0))), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v2"))
                .andExpect(jsonPath("$.data.rules.length()").value(1));

        List<Map<String, Object>> policies = jdbc.queryForList(
                "select id, version, active, policy_text from refund_policies where accommodation_id = ? order by id",
                listing.accommodationId());
        assertThat(policies).hasSize(2);
        assertThat(policies.get(0).get("id")).isEqualTo(firstPolicy.get("id"));
        assertThat(policies.get(0).get("active")).isEqualTo(false);
        assertThat(policies.get(0).get("policy_text")).isEqualTo("체크인 7일 전까지 전액 환불");
        assertThat(policies.get(1).get("active")).isEqualTo(true);
        // 과거 버전의 규칙도 지워지지 않는다
        assertThat(jdbc.queryForObject("select count(*) from refund_policy_rules where refund_policy_id = ?",
                Integer.class, firstPolicy.get("id"))).isEqualTo(3);

        mockMvc.perform(get("/api/accommodations/" + listing.accommodationId()))
                .andExpect(jsonPath("$.data.refundPolicy.version").value("v2"))
                .andExpect(jsonPath("$.data.refundPolicy.policyText").value("환불 불가"));
    }

    @Test
    void 환불정책_입력_검증() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/host/accommodations/" + listing.accommodationId() + "/refund-policy";

        putJson(path, body("rules", List.of()), host.token()).andExpect(status().isBadRequest());
        putJson(path, body("rules", List.of(body("daysBefore", 3, "refundRate", 1.5))), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        putJson(path, body("rules", List.of(body("daysBefore", 3, "refundRate", 1),
                body("daysBefore", 3, "refundRate", 0.5))), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-008"));
        assertThat(jdbc.queryForObject("select count(*) from refund_policies where accommodation_id = ?",
                Integer.class, listing.accommodationId())).isEqualTo(1);
    }

    // ---------- 목록 검색 ----------

    @Test
    void 목록에는_공개된_숙소만_나오고_지역과_인원으로_검색한다() throws Exception {
        Member host = createHost();
        Listing seogwipo = createListedAccommodation(host, SEOGWIPO);   // 최대 4명, 기본가 10만원
        Listing gangnam = createListedAccommodation(host, GANGNAM);
        long draftId = data(postJson("/api/host/accommodations", accommodationBody(SEOGWIPO, "작성 중"),
                host.token()).andExpect(status().isOk())).get("id").asLong();
        // 더 싼 객실을 추가하면 목록의 최저가가 바뀐다
        postJson("/api/host/accommodations/" + seogwipo.accommodationId() + "/rooms",
                roomBody("스탠다드", 2, 2, 0, 70000), host.token()).andExpect(status().isOk());

        // 전체: 최근 등록순이라 방금 만든 숙소가 첫 페이지에 있다
        List<Long> all = ids(search(""));
        assertThat(all).contains(seogwipo.accommodationId(), gangnam.accommodationId()).doesNotContain(draftId);
        assertThat(all.indexOf(gangnam.accommodationId())).isLessThan(all.indexOf(seogwipo.accommodationId()));

        // 시·도로 검색하면 하위 시·군·구의 숙소도 나온다
        JsonNode jeju = search("&regionId=" + JEJU);
        assertThat(ids(jeju)).contains(seogwipo.accommodationId())
                .doesNotContain(gangnam.accommodationId(), draftId);
        JsonNode row = find(jeju, seogwipo.accommodationId());
        assertThat(row.get("regionName").asText()).isEqualTo("서귀포시");
        assertThat(row.get("thumbnailUrl").asText()).isEqualTo("http://localhost:8080/uploads/a.jpg");
        assertThat(row.get("minNightlyPrice").asInt()).isEqualTo(70000);
        assertThat(ids(search("&regionId=" + SEOGWIPO))).contains(seogwipo.accommodationId());
        assertThat(ids(search("&regionId=" + SEOUL))).contains(gangnam.accommodationId())
                .doesNotContain(seogwipo.accommodationId());

        // 인원: 최대 인원이 그 이상인 객실이 있는 숙소만
        assertThat(ids(search("&regionId=" + JEJU + "&guestCount=4"))).contains(seogwipo.accommodationId());
        assertThat(ids(search("&regionId=" + JEJU + "&guestCount=5"))).doesNotContain(seogwipo.accommodationId());
        mockMvc.perform(get("/api/accommodations").param("guestCount", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void 목록은_페이지_단위로_나온다() throws Exception {
        Member host = createHost();
        createListedAccommodation(host, GANGNAM);
        createListedAccommodation(host, GANGNAM);

        mockMvc.perform(get("/api/accommodations").param("regionId", String.valueOf(GANGNAM))
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        // size는 최대 50으로 제한한다
        mockMvc.perform(get("/api/accommodations").param("size", "1000"))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    private JsonNode search(String query) throws Exception {
        return data(mockMvc.perform(get("/api/accommodations?size=50" + query)).andExpect(status().isOk()))
                .get("content");
    }

    private List<Long> ids(JsonNode content) {
        List<Long> ids = new ArrayList<>();
        content.forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private JsonNode find(JsonNode content, long id) {
        for (JsonNode row : content) {
            if (row.get("id").asLong() == id) {
                return row;
            }
        }
        throw new AssertionError("목록에 없는 숙소: " + id);
    }
}

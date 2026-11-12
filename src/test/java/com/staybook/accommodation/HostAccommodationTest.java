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
    private static final long HOTEL = 1L;
    private static final long HANOK = 3L;

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
        patchJson(path + "/listing-status", body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-007"));

        postJson(path + "/rooms", roomBody("디럭스", 2, 4, 20000, 120000), host.token()).andExpect(status().isOk());
        patchJson(path + "/listing-status", body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-007"));
        putJson(path + "/refund-policy", refundPolicyBody(), host.token()).andExpect(status().isOk());
        patchJson(path + "/listing-status", body("listingStatus", "LISTED"), host.token())
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
        patchJson(path + "/listing-status", body("listingStatus", "HIDDEN"), host.token()).andExpect(status().isOk());
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

    // ---------- 호스트 조회 ----------

    @Test
    void 내_숙소_목록에는_공개_상태와_관계없이_내_숙소만_최근_등록순으로_나온다() throws Exception {
        Member host = createHost();
        Member other = createHost();
        Listing listed = createListedAccommodation(host, SEOUL);
        Listing hidden = createListedAccommodation(host, SEOUL);
        patchJson("/api/host/accommodations/" + hidden.accommodationId() + "/listing-status",
                body("listingStatus", "HIDDEN"), host.token()).andExpect(status().isOk());
        long draftId = data(postJson("/api/host/accommodations", accommodationBody(GANGNAM, "작성 중"), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        Listing othersListing = createListedAccommodation(other, SEOUL);

        JsonNode mine = data(getWith("/api/host/accommodations", host.token()).andExpect(status().isOk()));
        assertThat(ids(mine)).containsExactly(draftId, hidden.accommodationId(), listed.accommodationId())
                .doesNotContain(othersListing.accommodationId());

        JsonNode draft = find(mine, draftId);
        assertThat(draft.get("listingStatus").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("regionName").asText()).isEqualTo("강남구");
        assertThat(draft.get("categoryName").asText()).isEqualTo("호텔·리조트");
        assertThat(draft.get("thumbnailUrl").asText()).isEqualTo("http://localhost:8080/uploads/a.jpg");
        assertThat(draft.get("roomCount").asInt()).isZero();
        assertThat(draft.get("hasRefundPolicy").asBoolean()).isFalse();
        assertThat(draft.get("readyToList").asBoolean()).isFalse();

        JsonNode hiddenRow = find(mine, hidden.accommodationId());
        assertThat(hiddenRow.get("listingStatus").asText()).isEqualTo("HIDDEN");
        assertThat(hiddenRow.get("roomCount").asInt()).isEqualTo(1);
        assertThat(hiddenRow.get("activeRoomCount").asInt()).isEqualTo(1);
        assertThat(hiddenRow.get("hasRefundPolicy").asBoolean()).isTrue();
        assertThat(hiddenRow.get("readyToList").asBoolean()).isTrue();

        // 숙소가 없는 호스트는 빈 목록, GUEST는 접근 불가
        getWith("/api/host/accommodations", createHost().token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        getWith("/api/host/accommodations", createMember().token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
    }

    @Test
    void 내_숙소_상세는_공개_전_숙소와_판매_중지_객실도_보여준다() throws Exception {
        Member host = createHost();
        Member other = createHost();
        long id = data(postJson("/api/host/accommodations", accommodationBody(SEOUL, "작성 중 상세"), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        String path = "/api/host/accommodations/" + id;
        postJson(path + "/rooms", roomBody("디럭스", 2, 4, 20000, 120000), host.token()).andExpect(status().isOk());
        long stoppedRoomId = data(postJson(path + "/rooms", roomBody("스위트", 2, 6, 30000, 250000), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        patchJson(path + "/rooms/" + stoppedRoomId, body("status", "INACTIVE"), host.token())
                .andExpect(status().isOk());

        // 공개 조회에는 없는 숙소다
        mockMvc.perform(get("/api/accommodations/" + id)).andExpect(status().isNotFound());

        getWith(path, host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("작성 중 상세"))
                .andExpect(jsonPath("$.data.listingStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.rooms.length()").value(2))
                .andExpect(jsonPath("$.data.rooms[1].id").value(stoppedRoomId))
                .andExpect(jsonPath("$.data.rooms[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.refundPolicy").doesNotExist());

        getWith(path, other.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        getWith("/api/host/accommodations/999999999", host.token())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-001"));
    }

    // ---------- 공개 상태 ----------

    @Test
    void 공개_상태는_전용_API로만_바뀌고_일반_수정에_섞어_보내도_무시된다() throws Exception {
        Member host = createHost();
        Member other = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/host/accommodations/" + listing.accommodationId();

        patchJson(path, body("name", "이름만 바뀐다", "listingStatus", "HIDDEN"), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("이름만 바뀐다"))
                .andExpect(jsonPath("$.data.listingStatus").value("LISTED"));
        mockMvc.perform(get("/api/accommodations/" + listing.accommodationId())).andExpect(status().isOk());

        patchJson(path + "/listing-status", body(), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        patchJson(path + "/listing-status", body("listingStatus", "PUBLIC"), host.token())
                .andExpect(status().isBadRequest());
        patchJson(path + "/listing-status", body("listingStatus", "HIDDEN"), other.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        assertThat(jdbc.queryForObject("select listing_status from accommodations where id = ?", String.class,
                listing.accommodationId())).isEqualTo("LISTED");

        // DRAFT로 되돌릴 수도 있다
        patchJson(path + "/listing-status", body("listingStatus", "DRAFT"), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listingStatus").value("DRAFT"));
    }

    @Test
    void 공개_중인_숙소의_마지막_판매_객실은_중지할_수_없다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/host/accommodations/" + listing.accommodationId();
        String firstRoom = path + "/rooms/" + listing.roomId();

        patchJson(firstRoom, body("status", "INACTIVE"), host.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-010"));
        assertThat(jdbc.queryForObject("select status from rooms where id = ?", String.class, listing.roomId()))
                .isEqualTo("ACTIVE");
        // 상태를 바꾸지 않는 수정은 마지막 객실이어도 된다
        patchJson(firstRoom, body("nightlyPrice", 110000, "status", "ACTIVE"), host.token())
                .andExpect(status().isOk());

        // 판매 객실이 2개면 하나는 끌 수 있고, 남은 하나는 다시 막힌다
        long secondRoomId = data(postJson(path + "/rooms", roomBody("스위트", 2, 6, 30000, 250000), host.token())
                .andExpect(status().isOk())).get("id").asLong();
        patchJson(firstRoom, body("status", "INACTIVE"), host.token()).andExpect(status().isOk());
        // 이미 꺼진 객실을 다시 INACTIVE로 보내는 것은 막지 않는다
        patchJson(firstRoom, body("status", "INACTIVE"), host.token()).andExpect(status().isOk());
        patchJson(path + "/rooms/" + secondRoomId, body("status", "INACTIVE"), host.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-010"));

        // 숙소를 숨기면 끌 수 있고, 판매 객실이 없으니 다시 공개할 수 없다
        patchJson(path + "/listing-status", body("listingStatus", "HIDDEN"), host.token())
                .andExpect(status().isOk());
        patchJson(path + "/rooms/" + secondRoomId, body("status", "INACTIVE"), host.token())
                .andExpect(status().isOk());
        patchJson(path + "/listing-status", body("listingStatus", "LISTED"), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-007"));
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
    void 카테고리_목록을_조회하고_카테고리로_검색한다() throws Exception {
        // 목록은 토큰 없이, 정렬 순서대로
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[0].id").value(HOTEL))
                .andExpect(jsonPath("$.data[0].name").value("호텔·리조트"))
                .andExpect(jsonPath("$.data[2].name").value("한옥"))
                .andExpect(jsonPath("$.data[7].name").value("기타"));

        Member host = createHost();
        Listing hotel = createListedAccommodation(host, SEOGWIPO);      // 기본: 호텔·리조트
        Listing hanok = createListedAccommodation(host, SEOGWIPO);
        Listing hanokSeoul = createListedAccommodation(host, GANGNAM);
        for (Listing listing : List.of(hanok, hanokSeoul)) {
            patchJson("/api/host/accommodations/" + listing.accommodationId(), body("categoryId", HANOK),
                    host.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryId").value(HANOK))
                    .andExpect(jsonPath("$.data.categoryName").value("한옥"));
        }

        JsonNode hanoks = search("&categoryId=" + HANOK);
        assertThat(ids(hanoks)).contains(hanok.accommodationId(), hanokSeoul.accommodationId())
                .doesNotContain(hotel.accommodationId());
        assertThat(find(hanoks, hanok.accommodationId()).get("categoryName").asText()).isEqualTo("한옥");
        assertThat(ids(search("&categoryId=" + HOTEL))).contains(hotel.accommodationId())
                .doesNotContain(hanok.accommodationId());

        // 다른 조건과 함께 쓰면 모두 만족하는 숙소만 나온다
        assertThat(ids(search("&categoryId=" + HANOK + "&regionId=" + JEJU))).contains(hanok.accommodationId())
                .doesNotContain(hanokSeoul.accommodationId(), hotel.accommodationId());
        assertThat(ids(search("&categoryId=" + HANOK + "&regionId=" + JEJU + "&guestCount=5")))
                .doesNotContain(hanok.accommodationId());
        // 없는 카테고리는 빈 목록
        assertThat(ids(search("&categoryId=999999"))).isEmpty();

        mockMvc.perform(get("/api/accommodations/" + hanok.accommodationId()))
                .andExpect(jsonPath("$.data.categoryId").value(HANOK))
                .andExpect(jsonPath("$.data.categoryName").value("한옥"));
    }

    @Test
    void 숙소_등록과_수정에서_카테고리를_검증한다() throws Exception {
        Member host = createHost();
        Map<String, Object> missing = accommodationBody(SEOUL, "카테고리 없음");
        missing.remove("categoryId");
        postJson("/api/host/accommodations", missing, host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
        Map<String, Object> unknown = accommodationBody(SEOUL, "없는 카테고리");
        unknown.put("categoryId", 999_999);
        postJson("/api/host/accommodations", unknown, host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-009"));

        long id = data(postJson("/api/host/accommodations", accommodationBody(SEOUL, "정상"), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("호텔·리조트"))).get("id").asLong();
        patchJson("/api/host/accommodations/" + id, body("categoryId", 999_999), host.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-009"));
        // 카테고리를 보내지 않은 수정은 카테고리를 바꾸지 않는다
        patchJson("/api/host/accommodations/" + id, body("name", "이름만 변경"), host.token())
                .andExpect(jsonPath("$.data.categoryId").value(HOTEL));
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

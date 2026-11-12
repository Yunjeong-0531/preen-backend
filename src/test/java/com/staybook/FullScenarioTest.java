package com.staybook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.reservation.service.ReservationService;
import com.staybook.support.IntegrationTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 7단계: 요구사항 10장의 통합 시나리오 1~7을 API 호출로 이어서 확인한다.
 * "회원가입 → 숙소 조회 → 예약 → 결제 → 취소/환불 → 리뷰" 한 사이클이 한 숙소를 두고 순서대로 진행된다.
 * 실제 MySQL·Redis와 가짜 PG를 쓰고, 시간이 지나야 하는 부분(선점 만료, 체크아웃 경과)은
 * DB의 시각을 과거로 옮긴 뒤 스케줄러 작업을 직접 호출한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullScenarioTest extends IntegrationTestSupport {

    private static final long SEOGWIPO = 1702L;
    private static final long JEJU = 17L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3, 4};

    @Autowired
    ReservationService reservationService;

    private final LocalDate today = LocalDate.now();
    /** 재고를 설정하는 기간의 첫날. 모든 날짜가 체크인 7일 전(전액 환불 구간)보다 멀다. */
    private final LocalDate firstDay = today.plusDays(20);

    // 시나리오 사이에 이어지는 상태
    private final String hostEmail = uniqueEmail();
    private String hostToken;
    private long accommodationId;
    private long roomId;
    private Member guest;
    private JsonNode paidReservation;

    // ---------------------------------------------------------------------------------------------
    // 1. 가입 → 로그인 → 사업자 등록 → 숙소·객실 등록 → 재고 설정
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(1)
    void 시나리오1_가입하고_로그인해서_사업자_등록_후_숙소와_객실을_등록하고_재고를_설정한다() throws Exception {
        // 가입: 이메일 인증 → 약관 동의와 생년월일을 포함해 가입
        postJson("/api/auth/email/resend", body("email", hostEmail)).andExpect(status().isOk());
        postJson("/api/auth/email/verify", body("email", hostEmail, "code", mail.lastCode(hostEmail)))
                .andExpect(status().isOk());
        postJson("/api/auth/signup", signupBody(hostEmail, today.minusYears(40), requiredTermIds()))
                .andExpect(status().isOk());

        // 로그인: 처음에는 GUEST
        String guestRoleToken = login(hostEmail, PASSWORD).get("accessToken").asText();
        getWith("/api/users/me", guestRoleToken).andExpect(jsonPath("$.data.role").value("GUEST"));

        // 사업자 등록: 바로 승인되고 HOST 토큰을 받는다
        JsonNode registered = data(postJson("/api/host-businesses",
                body("businessName", "제주 바다 스테이", "businessRegistrationNo", randomBusinessNo()), guestRoleToken)
                .andExpect(status().isOk()));
        assertThat(registered.path("business").get("approvalStatus").asText()).isEqualTo("APPROVED");
        hostToken = registered.get("accessToken").asText();
        getWith("/api/users/me", hostToken).andExpect(jsonPath("$.data.role").value("HOST"));

        // 이미지 업로드: 파일을 올려 받은 URL을 숙소에 저장한다
        String imageUrl = data(mockMvc.perform(withToken(
                        multipart("/api/images").file(new MockMultipartFile("file", "sea.png", "image/png", PNG)),
                        hostToken))
                .andExpect(status().isOk())).get("url").asText();
        String imagePath = imageUrl.substring(imageUrl.indexOf("/uploads/"));
        assertThat(mockMvc.perform(get(imagePath)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).isEqualTo(PNG);

        // 숙소 등록 (DRAFT) → 객실 등록 → 환불정책 설정 → 공개
        accommodationId = data(postJson("/api/host/accommodations",
                body("regionId", SEOGWIPO, "categoryId", 4, "name", "서귀포 오션뷰 " + UUID.randomUUID(), "description", "바다가 보이는 숙소",
                        "address", "제주 서귀포시 테스트로 1", "checkInTime", "15:00", "checkOutTime", "11:00",
                        "imageUrls", List.of(imageUrl), "amenityIds", List.of(1, 2)), hostToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listingStatus").value("DRAFT"))).get("id").asLong();
        roomId = data(postJson("/api/host/accommodations/" + accommodationId + "/rooms",
                roomBody("오션뷰 더블", 2, 4, 20000, 100000), hostToken).andExpect(status().isOk())).get("id").asLong();
        putJson("/api/host/accommodations/" + accommodationId + "/refund-policy", refundPolicyBody(), hostToken)
                .andExpect(status().isOk());
        patchJson("/api/host/accommodations/" + accommodationId + "/listing-status", body("listingStatus", "LISTED"), hostToken)
                .andExpect(status().isOk());

        // 재고 설정: 6일 동안 하루 1개, 1박 10만원
        putJson("/api/host/rooms/" + roomId + "/inventories",
                body("startDate", firstDay.toString(), "endDate", firstDay.plusDays(5).toString(),
                        "totalCount", 1, "price", 100000), hostToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        Files.deleteIfExists(Path.of("uploads").resolve(imagePath.substring("/uploads/".length())));
    }

    // ---------------------------------------------------------------------------------------------
    // 2. 다른 회원이 숙소 조회 → 예약 → 결제 → 예약 확정
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(2)
    void 시나리오2_다른_회원이_숙소를_조회하고_예약해서_결제하면_예약이_확정된다() throws Exception {
        // 숙소 조회는 로그인 없이 한다: 목록 검색 → 상세 → 예약 가능 여부
        JsonNode list = data(mockMvc.perform(get("/api/accommodations")
                .param("regionId", String.valueOf(JEJU)).param("categoryId", "4").param("guestCount", "3").param("size", "50"))
                .andExpect(status().isOk())).get("content");
        List<Long> ids = new ArrayList<>();
        list.forEach(row -> ids.add(row.get("id").asLong()));
        assertThat(ids).contains(accommodationId);

        mockMvc.perform(get("/api/accommodations/" + accommodationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].id").value(roomId))
                .andExpect(jsonPath("$.data.refundPolicy.rules.length()").value(3));
        mockMvc.perform(get("/api/accommodations/" + accommodationId + "/availability")
                        .param("checkIn", firstDay.toString()).param("checkOut", firstDay.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(true))
                .andExpect(jsonPath("$.data[0].totalPrice").value(200000));

        // 예약: 3명 2박 = 객실 20만 + 추가 인원 (3 - 2)명 × 2만 × 2박 = 24만
        guest = createMember();
        paidReservation = reserveOk(guest, roomId, firstDay, firstDay.plusDays(2), 3);
        long reservationId = paidReservation.get("id").asLong();
        assertThat(paidReservation.get("status").asText()).isEqualTo("PENDING");
        assertThat(paidReservation.get("totalPrice").asInt()).isEqualTo(240000);
        assertThat(inventoryCounts(roomId, firstDay)).isEqualTo("held=1,booked=0");

        // 결제: 준비 → 승인
        JsonNode prepared = data(postJson("/api/payments/prepare", body("reservationId", reservationId),
                guest.token()).andExpect(status().isOk()));
        assertThat(prepared.get("amount").asInt()).isEqualTo(240000);
        confirmPayment(guest, prepared.get("orderId").asText(), "pay-" + UUID.randomUUID(), 240000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        // 예약 확정
        getWith("/api/reservations/" + reservationId, guest.token())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(inventoryCounts(roomId, firstDay)).isEqualTo("held=0,booked=1");
        assertThat(inventoryCounts(roomId, firstDay.plusDays(1))).isEqualTo("held=0,booked=1");
        // 호스트의 예약 목록에도 보이고, 그 날짜는 더 이상 예약할 수 없다
        getWith("/api/host/reservations", hostToken)
                .andExpect(jsonPath("$.data.content[0].id").value(reservationId))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"));
        mockMvc.perform(get("/api/accommodations/" + accommodationId + "/availability")
                        .param("checkIn", firstDay.toString()).param("checkOut", firstDay.plusDays(1).toString()))
                .andExpect(jsonPath("$.data[0].available").value(false));
    }

    // ---------------------------------------------------------------------------------------------
    // 3. 예약 취소 → 환불정책에 따른 환불액 계산 → 재고 복구
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(3)
    void 시나리오3_예약을_취소하면_환불정책대로_환불액이_계산되고_재고가_복구된다() throws Exception {
        // 체크인까지 5일 남은 예약을 하나 더 만들어 결제한다 (3일 전까지 50% 구간)
        LocalDate nearCheckIn = today.plusDays(5);
        putJson("/api/host/rooms/" + roomId + "/inventories",
                body("startDate", nearCheckIn.toString(), "endDate", nearCheckIn.toString(),
                        "totalCount", 1, "price", 100000), hostToken).andExpect(status().isOk());
        JsonNode nearReservation = reserveOk(guest, roomId, nearCheckIn, nearCheckIn.plusDays(1), 2);
        pay(guest, nearReservation);

        // (가) 체크인 5일 전 취소: 50% 환불, 50% 위약금
        postJson("/api/reservations/" + nearReservation.get("id").asLong() + "/cancel", body(), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundRate").value(0.5))
                .andExpect(jsonPath("$.data.refundAmount").value(50000))
                .andExpect(jsonPath("$.data.penaltyAmount").value(50000));
        assertThat(refund(nearReservation.get("id").asLong())).isEqualTo("50000.00/50000.00/COMPLETED");
        assertThat(inventoryCounts(roomId, nearCheckIn)).isEqualTo("held=0,booked=0");

        // (나) 시나리오 2의 예약(체크인 20일 전) 취소: 전액 환불
        long reservationId = paidReservation.get("id").asLong();
        postJson("/api/reservations/" + reservationId + "/cancel", body(), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundRate").value(1.0))
                .andExpect(jsonPath("$.data.refundAmount").value(240000))
                .andExpect(jsonPath("$.data.penaltyAmount").value(0));
        assertThat(refund(reservationId)).isEqualTo("240000.00/0.00/COMPLETED");
        assertThat(jdbc.queryForObject("select status from payments where reservation_id = ?", String.class,
                reservationId)).isEqualTo("REFUNDED");

        // 재고 복구: 두 날짜 모두 다시 예약할 수 있다
        assertThat(inventoryCounts(roomId, firstDay)).isEqualTo("held=0,booked=0");
        assertThat(inventoryCounts(roomId, firstDay.plusDays(1))).isEqualTo("held=0,booked=0");
        mockMvc.perform(get("/api/accommodations/" + accommodationId + "/availability")
                        .param("checkIn", firstDay.toString()).param("checkOut", firstDay.plusDays(2).toString()))
                .andExpect(jsonPath("$.data[0].available").value(true));
    }

    // ---------------------------------------------------------------------------------------------
    // 4. 결제하지 않은 예약이 만료되어 재고가 복구됨
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(4)
    void 시나리오4_결제하지_않은_예약은_만료되어_재고가_복구된다() throws Exception {
        long reservationId = reserveOk(guest, roomId, firstDay, firstDay.plusDays(1), 2).get("id").asLong();
        assertThat(inventoryCounts(roomId, firstDay)).isEqualTo("held=1,booked=0");

        // 결제 제한 시간(10분)이 지난 것으로 만들고 1분 주기 스케줄러 작업을 실행한다
        jdbc.update("update reservations set hold_expires_at = now(6) - interval 1 second where id = ?",
                reservationId);
        reservationService.expirePending();

        getWith("/api/reservations/" + reservationId, guest.token())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(inventoryCounts(roomId, firstDay)).isEqualTo("held=0,booked=0");
        assertThat(jdbc.queryForList("select status from inventory_holds where reservation_id = ?", String.class,
                reservationId)).containsExactly("RELEASED");
    }

    // ---------------------------------------------------------------------------------------------
    // 5. 재고가 1개인 날짜에 동시에 두 건을 예약하면 한 건만 성공함
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(5)
    void 시나리오5_재고가_1개인_날짜에_동시에_두_건을_예약하면_한_건만_성공한다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        // 우연히 통과하는 일이 없도록 서로 다른 세 날짜에서 반복한다
        for (int round = 0; round < 3; round++) {
            LocalDate checkIn = firstDay.plusDays(2 + round);
            Member first = createMember();
            Member second = createMember();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (Member member : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return reserve(member, roomId, checkIn, checkIn.plusDays(1), 2)
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            // 한 건은 성공(200), 다른 한 건은 매진(409)
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(inventoryCounts(roomId, checkIn)).isEqualTo("held=1,booked=0");
            assertThat(jdbc.queryForObject(
                    "select count(*) from reservations where room_id = ? and check_in = ? and status = 'PENDING'",
                    Integer.class, roomId, checkIn)).isEqualTo(1);
        }
        pool.shutdown();
    }

    // ---------------------------------------------------------------------------------------------
    // 6. 체크아웃 후 리뷰 작성, 같은 예약으로 두 번째 리뷰는 거부됨
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(6)
    void 시나리오6_체크아웃_후_리뷰를_쓰고_같은_예약의_두_번째_리뷰는_거부된다() throws Exception {
        JsonNode reservation = reserveOk(guest, roomId, firstDay, firstDay.plusDays(1), 2);
        long reservationId = reservation.get("id").asLong();
        pay(guest, reservation);
        var review = body("reservationId", reservationId, "rating", 5, "content", "바다가 정말 잘 보여요.",
                "spotifyTrackId", "4uLU6hMCjMI75M1A2tKUQC");

        // 아직 투숙 전이라 쓸 수 없다
        postJson("/api/reviews", review, guest.token())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW-003"));

        // 체크아웃이 지난 것으로 만들고 하루 주기 스케줄러 작업을 실행한다
        jdbc.update("update reservations set check_in = ?, check_out = ? where id = ?",
                today.minusDays(2), today.minusDays(1), reservationId);
        reservationService.completeStays();
        getWith("/api/reservations/" + reservationId, guest.token())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        long reviewId = data(postJson("/api/reviews", review, guest.token()).andExpect(status().isOk()))
                .get("id").asLong();
        postJson("/api/reviews", review, guest.token())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW-004"));

        // 호스트가 답글을 달고, 리뷰는 로그인 없이 보인다. 찜도 한다.
        postJson("/api/host/reviews/" + reviewId + "/reply", body("reply", "다음에 또 오세요."), hostToken)
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/accommodations/" + accommodationId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[0].spotifyTrackId").value("4uLU6hMCjMI75M1A2tKUQC"))
                .andExpect(jsonPath("$.data.content[0].hostReply").value("다음에 또 오세요."));
        mockMvc.perform(withToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/favorites/" + accommodationId), guest.token())).andExpect(status().isOk());
        getWith("/api/favorites/me", guest.token())
                .andExpect(jsonPath("$.data.content[0].accommodation.id").value(accommodationId));
    }

    // ---------------------------------------------------------------------------------------------
    // 7. 미성년 회원의 예약, GUEST의 /api/host/** 접근이 거부됨
    // ---------------------------------------------------------------------------------------------
    @Test
    @Order(7)
    void 시나리오7_미성년_회원의_예약과_GUEST의_호스트_경로_접근은_거부된다() throws Exception {
        // 만 16세: 가입, 조회, 찜은 되지만 예약은 안 된다
        String minorEmail = uniqueEmail();
        signUp(minorEmail, today.minusYears(16));
        Member minor = new Member(0, minorEmail, login(minorEmail, PASSWORD).get("accessToken").asText());
        LocalDate checkIn = firstDay.plusDays(5);

        mockMvc.perform(get("/api/accommodations/" + accommodationId)).andExpect(status().isOk());
        reserve(minor, roomId, checkIn, checkIn.plusDays(1), 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESERVATION-002"));
        assertThat(inventoryCounts(roomId, checkIn)).isEqualTo("held=0,booked=0");

        // GUEST는 호스트 경로 어디에도 접근할 수 없다
        String guestToken = guest.token();
        getWith("/api/host/business", guestToken).andExpect(status().isForbidden());
        getWith("/api/host/reservations", guestToken).andExpect(status().isForbidden());
        postJson("/api/host/accommodations", accommodationBody(SEOGWIPO, "게스트의 숙소"), guestToken)
                .andExpect(status().isForbidden());
        patchJson("/api/host/accommodations/" + accommodationId, body("name", "바꾸기"), guestToken)
                .andExpect(status().isForbidden());
        postJson("/api/host/accommodations/" + accommodationId + "/rooms", roomBody("몰래", 2, 2, 0, 1000),
                guestToken).andExpect(status().isForbidden());
        putJson("/api/host/accommodations/" + accommodationId + "/refund-policy", refundPolicyBody(), guestToken)
                .andExpect(status().isForbidden());
        putJson("/api/host/rooms/" + roomId + "/inventories",
                body("startDate", checkIn.toString(), "endDate", checkIn.toString(), "totalCount", 9), guestToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
        postJson("/api/host/reviews/1/reply", body("reply", "게스트의 답글"), guestToken)
                .andExpect(status().isForbidden());
        // ADMIN 경로도 규칙만 등록돼 있어 HOST조차 접근할 수 없다
        getWith("/api/admin/anything", hostToken).andExpect(status().isForbidden());

        // 숙소 이름과 재고는 그대로다
        assertThat(jdbc.queryForObject("select total_count from room_inventories where room_id = ? and stay_date = ?",
                Integer.class, roomId, checkIn)).isEqualTo(1);
    }

    /** "환불액/위약금/상태" */
    private String refund(long reservationId) {
        return jdbc.queryForObject("select concat(r.amount, '/', r.penalty_amount, '/', r.status) from refunds r "
                + "join payments p on p.id = r.payment_id where p.reservation_id = ?", String.class, reservationId);
    }
}

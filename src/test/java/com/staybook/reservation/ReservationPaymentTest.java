package com.staybook.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.payment.pg.FakePgClient;
import com.staybook.reservation.service.ReservationService;
import com.staybook.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 5단계(reservation, payment) 통합 테스트. 실제 MySQL·Redis와 가짜 PG를 쓴다. */
class ReservationPaymentTest extends IntegrationTestSupport {

    private static final long SEOUL = 1L;

    @Autowired
    ReservationService reservationService;
    @Autowired
    FakePgClient fakePg;

    private final LocalDate today = LocalDate.now();

    /** 재고 1개, 1박 10만원으로 checkIn부터 3박을 팔 수 있는 숙소. 객실은 기준 2명, 최대 4명, 추가 1인 2만원. */
    private Listing listingWithStock(Member host, LocalDate checkIn, int totalCount) throws Exception {
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), checkIn, checkIn.plusDays(2), totalCount, 100000);
        return listing;
    }

    private List<String> history(long reservationId) {
        return jdbc.query("select previous_status, new_status, changed_by, reason from reservation_status_history "
                        + "where reservation_id = ? order by id",
                (rs, i) -> rs.getString(1) + ">" + rs.getString(2) + ":"
                        + (rs.getObject(3) == null ? "system" : "user") + ":" + rs.getString(4), reservationId);
    }

    private Map<String, Object> payment(String orderId) {
        return jdbc.queryForMap("select id, status, amount, method, payment_key, paid_at, pg_provider "
                + "from payments where order_id = ?", orderId);
    }

    private List<Map<String, Object>> refunds(long reservationId) {
        return jdbc.queryForList("select r.amount, r.penalty_amount, r.status, r.refunded_at, r.reason from refunds r "
                + "join payments p on p.id = r.payment_id where p.reservation_id = ? order by r.id", reservationId);
    }

    private ResultActions cancel(Member member, long reservationId) throws Exception {
        return postJson("/api/reservations/" + reservationId + "/cancel", body(), member.token());
    }

    private ResultActions webhook(String body, String signature) throws Exception {
        var request = post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("X-Fake-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String webhookBody(String eventId, String orderId, String paymentKey, String status) throws Exception {
        return objectMapper.writeValueAsString(body("eventId", eventId, "eventType", "PAYMENT_STATUS_CHANGED",
                "orderId", orderId, "paymentKey", paymentKey, "status", status));
    }

    // ---------- 예약 생성 ----------

    @Test
    void 예약을_만들면_PENDING이고_재고가_선점되며_요금_항목과_환불정책_스냅샷이_저장된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        // 둘째 날만 15만원
        putJson("/api/host/rooms/" + listing.roomId() + "/inventories",
                body("startDate", checkIn.plusDays(1).toString(), "endDate", checkIn.plusDays(1).toString(),
                        "price", 150000), host.token()).andExpect(status().isOk());

        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(2), 3);

        long id = reservation.get("id").asLong();
        assertThat(reservation.get("status").asText()).isEqualTo("PENDING");
        assertThat(reservation.get("reservationNo").asText()).matches("R\\d{6}-[A-Z2-9]{12}");
        assertThat(reservation.get("roomName").asText()).isEqualTo("디럭스");
        assertThat(reservation.get("guestName").asText()).isEqualTo("테스트회원");
        assertThat(reservation.get("guestPhone").asText()).isEqualTo("010-1234-5678");
        // ROOM = 10만 + 15만, EXTRA_PERSON = (3 - 2)명 × 2만 × 2박
        assertThat(reservation.get("totalPrice").asInt()).isEqualTo(290000);
        assertThat(reservation.get("priceItems")).hasSize(2);
        assertThat(reservation.get("priceItems").get(0).get("itemType").asText()).isEqualTo("ROOM");
        assertThat(reservation.get("priceItems").get(0).get("amount").asInt()).isEqualTo(250000);
        assertThat(reservation.get("priceItems").get(1).get("itemType").asText()).isEqualTo("EXTRA_PERSON");
        assertThat(reservation.get("priceItems").get(1).get("amount").asInt()).isEqualTo(40000);
        assertThat(reservation.get("refundPolicy").get("version").asText()).isEqualTo("v1");
        assertThat(reservation.get("refundPolicy").get("rules")).hasSize(3);

        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=1,booked=0");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(1))).isEqualTo("held=1,booked=0");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(2))).isEqualTo("held=0,booked=0");
        assertThat(history(id)).containsExactly("null>PENDING:user:예약 생성");

        Map<String, Object> row = jdbc.queryForMap(
                "select guest_phone, total_price, refund_policy_id, "
                        + "timestampdiff(second, created_at, hold_expires_at) as hold_seconds, "
                        + "json_extract(refund_policy_snapshot, '$.rules[1].refundRate') as second_rate, "
                        + "(select sum(amount) from reservation_price_items i where i.reservation_id = r.id) as items "
                        + "from reservations r where id = ?", id);
        assertThat((String) row.get("guest_phone")).startsWith("v1:").doesNotContain("1234");
        assertThat(row.get("total_price")).isEqualTo(row.get("items"));
        assertThat(((Number) row.get("hold_seconds")).intValue()).isEqualTo(600);
        assertThat(new BigDecimal(row.get("second_rate").toString())).isEqualByComparingTo("0.5");
    }

    @Test
    void 기준_인원_이하면_추가_인원_항목이_없고_예약자_정보를_직접_줄_수_있다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);

        JsonNode reservation = data(postJson("/api/reservations",
                body("roomId", listing.roomId(), "checkIn", checkIn.toString(),
                        "checkOut", checkIn.plusDays(1).toString(), "guestCount", 2,
                        "guestName", "대신 묵는 사람", "guestPhone", "010-7777-8888"), guest.token())
                .andExpect(status().isOk()));

        assertThat(reservation.get("priceItems")).hasSize(1);
        assertThat(reservation.get("totalPrice").asInt()).isEqualTo(100000);
        assertThat(reservation.get("guestName").asText()).isEqualTo("대신 묵는 사람");
        assertThat(reservation.get("guestPhone").asText()).isEqualTo("010-7777-8888");
    }

    @Test
    void 미성년_회원은_예약할_수_없다() throws Exception {
        Member host = createHost();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        String email = uniqueEmail();
        signUp(email, today.minusYears(17));
        Member minor = new Member(0, email, login(email, PASSWORD).get("accessToken").asText());

        reserve(minor, listing.roomId(), checkIn, checkIn.plusDays(1), 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESERVATION-002"));

        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        postJson("/api/reservations", body("roomId", listing.roomId())).andExpect(status().isUnauthorized());
    }

    @Test
    void 예약_생성_조건을_어기면_거부한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);

        reserve(guest, listing.roomId(), checkIn, checkIn, 2)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESERVATION-003"));
        reserve(guest, listing.roomId(), today.minusDays(1), today.plusDays(1), 2)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESERVATION-003"));
        reserve(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 5)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESERVATION-004"));
        reserve(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 0)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-001"));
        reserve(guest, 999_999_999L, checkIn, checkIn.plusDays(1), 2).andExpect(status().isNotFound());
        // 재고를 설정하지 않은 날짜
        reserve(guest, listing.roomId(), checkIn.plusDays(5), checkIn.plusDays(6), 2)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVENTORY-001"));

        // 공개하지 않은 숙소의 객실
        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/listing-status", body("listingStatus", "HIDDEN"),
                host.token()).andExpect(status().isOk());
        reserve(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION-005"));

        assertThat(jdbc.queryForObject("select count(*) from reservations where room_id = ?", Integer.class,
                listing.roomId())).isZero();
    }

    @Test
    void 매진이면_예약이_남지_않고_먼저_잡은_날짜의_재고도_되돌아간다() throws Exception {
        Member host = createHost();
        Member first = createMember();
        Member second = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        reserveOk(first, listing.roomId(), checkIn.plusDays(1), checkIn.plusDays(2), 2);   // 둘째 날을 먼저 예약

        reserve(second, listing.roomId(), checkIn, checkIn.plusDays(3), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY-001"));

        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(1))).isEqualTo("held=1,booked=0");
        assertThat(jdbc.queryForObject("select count(*) from reservations where room_id = ?", Integer.class,
                listing.roomId())).isEqualTo(1);
    }

    // ---------- 결제 ----------

    @Test
    void 결제하면_PAID가_되고_예약이_확정되며_재고가_확정으로_옮겨진다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(2), 2);
        long id = reservation.get("id").asLong();

        JsonNode prepared = data(postJson("/api/payments/prepare", body("reservationId", id), guest.token())
                .andExpect(status().isOk()));
        String orderId = prepared.get("orderId").asText();
        assertThat(orderId).startsWith("ORD-");
        assertThat(prepared.get("amount").asInt()).isEqualTo(200000);
        assertThat(prepared.get("pgProvider").asText()).isEqualTo("FAKE");
        assertThat(payment(orderId)).containsEntry("status", "READY");
        // 다시 준비해도 같은 주문 번호를 준다
        assertThat(preparePayment(guest, id)).isEqualTo(orderId);

        String paymentKey = "pay-" + UUID.randomUUID();
        confirmPayment(guest, orderId, paymentKey, 200000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.method").value("CARD"))
                .andExpect(jsonPath("$.data.reservationId").value(id));

        Map<String, Object> paid = payment(orderId);
        assertThat(paid).containsEntry("status", "PAID").containsEntry("payment_key", paymentKey);
        assertThat(paid.get("paid_at")).isNotNull();
        assertThat(reservationStatus(id)).isEqualTo("CONFIRMED");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=1");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(1))).isEqualTo("held=0,booked=1");
        assertThat(history(id)).containsExactly("null>PENDING:user:예약 생성", "PENDING>CONFIRMED:system:결제 완료");
        assertThat(jdbc.queryForList("select concat(event_type, ':', response_code) from payment_logs "
                + "where payment_id = ?", String.class, paid.get("id"))).containsExactly("APPROVE:DONE");
        // 확정된 예약은 다시 결제 준비를 할 수 없다
        postJson("/api/payments/prepare", body("reservationId", id), guest.token())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT-003"));
    }

    @Test
    void 요청_금액이_예약_금액과_다르면_PG를_호출하지_않고_거부한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);
        int approveCallsBefore = fakePg.approveCalls();

        confirmPayment(guest, orderId, "pay-" + UUID.randomUUID(), 1000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT-004"));

        assertThat(fakePg.approveCalls()).isEqualTo(approveCallsBefore);
        assertThat(payment(orderId)).containsEntry("status", "READY");
        assertThat(reservationStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void 같은_주문_번호로_다시_요청해도_결제는_한_번만_처리한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);
        String paymentKey = "pay-" + UUID.randomUUID();
        int approveCallsBefore = fakePg.approveCalls();

        confirmPayment(guest, orderId, paymentKey, 100000).andExpect(status().isOk());
        confirmPayment(guest, orderId, paymentKey, 100000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        assertThat(fakePg.approveCalls()).isEqualTo(approveCallsBefore + 1);
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=1");
        assertThat(history(id)).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from payment_logs where payment_id = ?", Integer.class,
                payment(orderId).get("id"))).isEqualTo(1);
    }

    @Test
    void 남의_예약은_결제_준비도_승인도_할_수_없다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);

        postJson("/api/payments/prepare", body("reservationId", id), stranger.token())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PAYMENT-002"));
        confirmPayment(stranger, orderId, "pay-" + UUID.randomUUID(), 100000)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PAYMENT-002"));
        confirmPayment(guest, "ORD-does-not-exist", "pay-" + UUID.randomUUID(), 100000)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT-001"));
        postJson("/api/payments/prepare", body("reservationId", id)).andExpect(status().isUnauthorized());
        assertThat(payment(orderId)).containsEntry("status", "READY");
    }

    @Test
    void PG_승인이_실패하면_FAILED로_남고_예약은_PENDING이라_다시_결제할_수_있다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        String orderId = preparePayment(guest, id);

        confirmPayment(guest, orderId, "fail-" + UUID.randomUUID(), 100000)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT-005"));

        Map<String, Object> failed = payment(orderId);
        assertThat(failed).containsEntry("status", "FAILED");
        assertThat(jdbc.queryForList("select response_code from payment_logs where payment_id = ?", String.class,
                failed.get("id"))).containsExactly("REJECT_CARD_COMPANY");
        assertThat(reservationStatus(id)).isEqualTo("PENDING");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=1,booked=0");
        // 끝난 주문 번호로는 다시 승인할 수 없다
        confirmPayment(guest, orderId, "pay-" + UUID.randomUUID(), 100000)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT-006"));

        // 새 주문 번호로 다시 결제한다
        assertThat(preparePayment(guest, id)).isNotEqualTo(orderId);
        pay(guest, reservation);
        assertThat(reservationStatus(id)).isEqualTo("CONFIRMED");
    }

    // ---------- 취소·환불 ----------

    @Test
    void 체크인_7일_전까지_취소하면_전액_환불되고_재고가_복구된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(2), 3);   // 24만원
        long id = reservation.get("id").asLong();
        pay(guest, reservation);

        cancel(guest, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundRate").value(1.0))
                .andExpect(jsonPath("$.data.refundAmount").value(240000))
                .andExpect(jsonPath("$.data.penaltyAmount").value(0));

        assertThat(reservationStatus(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancelled_at from reservations where id = ?", Object.class, id))
                .isNotNull();
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(1))).isEqualTo("held=0,booked=0");
        assertThat(history(id)).last().isEqualTo("CONFIRMED>CANCELLED:user:사용자 취소");

        List<Map<String, Object>> refunds = refunds(id);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0)).containsEntry("amount", new BigDecimal("240000.00"))
                .containsEntry("penalty_amount", new BigDecimal("0.00")).containsEntry("status", "COMPLETED");
        assertThat(refunds.get(0).get("refunded_at")).isNotNull();
        assertThat(jdbc.queryForObject("select status from payments where reservation_id = ?", String.class, id))
                .isEqualTo("REFUNDED");

        // 취소된 날짜는 다른 회원이 다시 예약할 수 있다
        reserveOk(createMember(), listing.roomId(), checkIn, checkIn.plusDays(2), 2);
        // 이미 취소한 예약은 다시 취소할 수 없다
        cancel(guest, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION-006"));
    }

    @Test
    void 체크인_3일_전까지는_절반만_환불하고_나머지는_위약금이다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(5);
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), checkIn, checkIn, 1, 33333);   // 절반이 원 단위로 떨어지지 않는 금액
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        pay(guest, reservation);

        cancel(guest, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundRate").value(0.5))
                .andExpect(jsonPath("$.data.refundAmount").value(16666))
                .andExpect(jsonPath("$.data.penaltyAmount").value(16667));

        assertThat(refunds(id).get(0)).containsEntry("amount", new BigDecimal("16666.00"))
                .containsEntry("penalty_amount", new BigDecimal("16667.00")).containsEntry("status", "COMPLETED");
    }

    @Test
    void 환불_구간이_지나면_환불액이_없고_전액_위약금이며_PG_취소를_호출하지_않는다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(1);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        pay(guest, reservation);
        int cancelCallsBefore = fakePg.cancelCalls();

        cancel(guest, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundRate").value(0.0))
                .andExpect(jsonPath("$.data.refundAmount").value(0))
                .andExpect(jsonPath("$.data.penaltyAmount").value(100000));

        assertThat(fakePg.cancelCalls()).isEqualTo(cancelCallsBefore);
        assertThat(refunds(id).get(0)).containsEntry("amount", new BigDecimal("0.00"))
                .containsEntry("penalty_amount", new BigDecimal("100000.00"));
        assertThat(jdbc.queryForObject("select status from payments where reservation_id = ?", String.class, id))
                .isEqualTo("PAID");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
    }

    @Test
    void 환불정책이_바뀌어도_예약_당시의_스냅샷으로_환불한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        pay(guest, reservation);
        // 예약 뒤에 호스트가 "환불 불가"로 바꾼다
        putJson("/api/host/accommodations/" + listing.accommodationId() + "/refund-policy",
                body("policyText", "환불 불가", "rules", List.of(body("daysBefore", 0, "refundRate", 0))),
                host.token()).andExpect(status().isOk());

        cancel(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundAmount").value(100000));

        assertThat(refunds(id).get(0)).containsEntry("amount", new BigDecimal("100000.00"));
    }

    @Test
    void 결제_전에_취소하면_재고만_되돌리고_환불은_없다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);

        cancel(guest, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(0));

        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        assertThat(history(id)).containsExactly("null>PENDING:user:예약 생성", "PENDING>CANCELLED:user:결제 전 취소");
        assertThat(refunds(id)).isEmpty();
        // 취소한 예약은 결제할 수 없다 (승인 시점에 다시 확인한다)
        confirmPayment(guest, orderId, "pay-" + UUID.randomUUID(), 100000)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT-007"));
    }

    @Test
    void PG_취소가_실패하면_환불은_FAILED로_남고_예약_취소는_유지된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        confirmPayment(guest, preparePayment(guest, id), "refund-fail-" + UUID.randomUUID(), 100000)
                .andExpect(status().isOk());

        cancel(guest, id).andExpect(status().isOk());

        assertThat(reservationStatus(id)).isEqualTo("CANCELLED");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        assertThat(refunds(id).get(0)).containsEntry("status", "FAILED")
                .containsEntry("amount", new BigDecimal("100000.00"));
        assertThat(refunds(id).get(0).get("refunded_at")).isNull();
        assertThat(jdbc.queryForObject("select status from payments where reservation_id = ?", String.class, id))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForList("select concat(l.event_type, ':', l.response_code) from payment_logs l "
                + "join payments p on p.id = l.payment_id where p.reservation_id = ? order by l.id",
                String.class, id)).containsExactly("APPROVE:DONE", "CANCEL:CANCEL_FAILED");
    }

    @Test
    void 남의_예약은_취소할_수_없다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();

        cancel(stranger, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION-001"));
        cancel(host, id).andExpect(status().isNotFound());

        assertThat(reservationStatus(id)).isEqualTo("PENDING");
    }

    // ---------- 선점 만료 ----------

    @Test
    void 결제하지_않은_예약은_만료되어_취소되고_재고가_복구된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long expiredId = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        long freshId = reserveOk(guest, listing.roomId(), checkIn.plusDays(1), checkIn.plusDays(2), 2)
                .get("id").asLong();

        // 아직 제한 시간 안이면 아무것도 취소하지 않는다
        reservationService.expirePending();
        assertThat(reservationStatus(expiredId)).isEqualTo("PENDING");

        jdbc.update("update reservations set hold_expires_at = now(6) - interval 1 second where id = ?", expiredId);
        reservationService.expirePending();

        assertThat(reservationStatus(expiredId)).isEqualTo("CANCELLED");
        assertThat(reservationStatus(freshId)).isEqualTo("PENDING");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
        assertThat(inventoryCounts(listing.roomId(), checkIn.plusDays(1))).isEqualTo("held=1,booked=0");
        assertThat(history(expiredId)).containsExactly("null>PENDING:user:예약 생성",
                "PENDING>CANCELLED:system:결제 시간 만료");
        assertThat(jdbc.queryForList("select status from inventory_holds where reservation_id = ?", String.class,
                expiredId)).containsExactly("RELEASED");

        // 다시 돌려도 결과가 같다
        reservationService.expirePending();
        assertThat(history(expiredId)).hasSize(2);
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
    }

    @Test
    void 결제는_성공했는데_예약이_이미_만료로_취소됐다면_즉시_전액_환불한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);
        jdbc.update("update reservations set hold_expires_at = now(6) - interval 1 second where id = ?", id);
        reservationService.expirePending();

        confirmPayment(guest, orderId, "pay-" + UUID.randomUUID(), 100000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-007"));

        assertThat(reservationStatus(id)).isEqualTo("CANCELLED");
        assertThat(payment(orderId)).containsEntry("status", "REFUNDED");
        assertThat(refunds(id)).hasSize(1);
        assertThat(refunds(id).get(0)).containsEntry("amount", new BigDecimal("100000.00"))
                .containsEntry("penalty_amount", new BigDecimal("0.00")).containsEntry("status", "COMPLETED");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=0");
    }

    @Test
    void 제한_시간이_지났어도_스케줄러가_취소하기_전에_결제가_끝나면_확정된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        long id = reservation.get("id").asLong();
        jdbc.update("update reservations set hold_expires_at = now(6) - interval 1 second where id = ?", id);

        pay(guest, reservation);
        reservationService.expirePending();

        assertThat(reservationStatus(id)).isEqualTo("CONFIRMED");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=1");
    }

    // ---------- 웹훅 ----------

    @Test
    void 웹훅은_서명이_맞아야_받고_같은_이벤트는_한_번만_처리한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);
        String eventId = "evt-" + UUID.randomUUID();
        String body = webhookBody(eventId, orderId, "pay-" + UUID.randomUUID(), "PAID");

        webhook(body, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PAYMENT-008"));
        webhook(body, sign(body + " ")).andExpect(status().isUnauthorized());
        webhook(body, "not-hex").andExpect(status().isUnauthorized());
        assertThat(payment(orderId)).containsEntry("status", "READY");

        // 토큰 없이, 서명만으로 받는다
        webhook(body, sign(body)).andExpect(status().isOk());
        assertThat(payment(orderId)).containsEntry("status", "PAID");
        assertThat(reservationStatus(id)).isEqualTo("CONFIRMED");
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=1");

        // 같은 이벤트가 다시 와도 성공으로 응답하되 아무것도 하지 않는다
        webhook(body, sign(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select concat(event_type, ':', provider_event_id) from payment_logs "
                + "where payment_id = ?", String.class, payment(orderId).get("id")))
                .containsExactly("PAYMENT_STATUS_CHANGED:" + eventId);
        assertThat(history(id)).hasSize(2);
    }

    @Test
    void 실패_웹훅은_결제를_FAILED로_바꾸고_알_수_없는_주문과_깨진_본문은_안전하게_처리한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        long id = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        String orderId = preparePayment(guest, id);

        String failed = webhookBody("evt-" + UUID.randomUUID(), orderId, null, "FAILED");
        webhook(failed, sign(failed)).andExpect(status().isOk());
        assertThat(payment(orderId)).containsEntry("status", "FAILED");
        assertThat(reservationStatus(id)).isEqualTo("PENDING");

        String unknown = webhookBody("evt-" + UUID.randomUUID(), "ORD-unknown", "pay-x", "PAID");
        webhook(unknown, sign(unknown)).andExpect(status().isOk());

        String broken = "{not json";
        webhook(broken, sign(broken)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));
    }

    // ---------- 완료 처리·조회 ----------

    @Test
    void 체크아웃이_지난_확정_예약만_COMPLETED가_된다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 2);
        JsonNode stayed = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        JsonNode upcoming = reserveOk(guest, listing.roomId(), checkIn.plusDays(1), checkIn.plusDays(2), 2);
        JsonNode unpaid = reserveOk(guest, listing.roomId(), checkIn.plusDays(2), checkIn.plusDays(3), 2);
        pay(guest, stayed);
        pay(guest, upcoming);
        // 투숙이 끝난 것으로 만든다 (어제 체크아웃)
        for (JsonNode r : List.of(stayed, unpaid)) {
            jdbc.update("update reservations set check_in = ?, check_out = ? where id = ?",
                    today.minusDays(2), today.minusDays(1), r.get("id").asLong());
        }

        reservationService.completeStays();

        assertThat(reservationStatus(stayed.get("id").asLong())).isEqualTo("COMPLETED");
        assertThat(reservationStatus(upcoming.get("id").asLong())).isEqualTo("CONFIRMED");
        assertThat(reservationStatus(unpaid.get("id").asLong())).isEqualTo("PENDING");
        assertThat(history(stayed.get("id").asLong())).last().isEqualTo("CONFIRMED>COMPLETED:system:체크아웃 경과");
        // 재고의 확정 수량은 그대로 남는다
        assertThat(inventoryCounts(listing.roomId(), checkIn)).isEqualTo("held=0,booked=1");
        // 끝난 예약은 취소할 수 없다
        cancel(guest, stayed.get("id").asLong()).andExpect(status().isConflict());
    }

    @Test
    void 내_예약_목록과_상세는_본인만_보고_호스트는_자기_숙소의_예약만_본다() throws Exception {
        Member host = createHost();
        Member otherHost = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = listingWithStock(host, checkIn, 1);
        createListedAccommodation(otherHost, SEOUL);
        long first = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2).get("id").asLong();
        long second = reserveOk(guest, listing.roomId(), checkIn.plusDays(1), checkIn.plusDays(2), 2)
                .get("id").asLong();

        getWith("/api/reservations/me", guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(second))
                .andExpect(jsonPath("$.data.content[1].id").value(first))
                .andExpect(jsonPath("$.data.content[1].priceItems[0].itemType").value("ROOM"));
        getWith("/api/reservations/me", stranger.token()).andExpect(jsonPath("$.data.totalElements").value(0));
        getWith("/api/reservations/" + first, guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.accommodationId").value(listing.accommodationId()));
        getWith("/api/reservations/" + first, stranger.token())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION-001"));
        getWith("/api/reservations/" + first, null).andExpect(status().isUnauthorized());

        getWith("/api/host/reservations", host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].guestName").value("테스트회원"));
        getWith("/api/host/reservations", otherHost.token()).andExpect(jsonPath("$.data.totalElements").value(0));
        getWith("/api/host/reservations", guest.token()).andExpect(status().isForbidden());
    }
}

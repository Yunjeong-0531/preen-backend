package com.staybook.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.common.exception.BaseException;
import com.staybook.inventory.dto.InventoryDtos.NightPrice;
import com.staybook.inventory.exception.InventoryErrorCode;
import com.staybook.inventory.service.InventoryService;
import com.staybook.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 4단계(inventory) 통합 테스트. 실제 MySQL을 쓴다. */
class InventoryTest extends IntegrationTestSupport {

    private static final long SEOUL = 1L;

    @Autowired
    InventoryService inventoryService;

    private final LocalDate day1 = LocalDate.now().plusDays(10);
    private final LocalDate day2 = day1.plusDays(1);
    private final LocalDate day3 = day1.plusDays(2);
    private final LocalDate day4 = day1.plusDays(3);

    /**
     * 선점에 쓸 예약을 만든다. inventory_holds.reservation_id가 예약을 참조(FK)하므로 실제 행이 필요하다.
     * 예약 API를 거치지 않고 재고 서비스만 따로 검증하기 위해 최소한의 값으로 직접 넣는다.
     */
    private long newReservationId(Listing listing) {
        String reservationNo = "T" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 31);
        jdbc.update("""
                insert into reservations (reservation_no, user_id, room_id, refund_policy_id, check_in, check_out,
                    guest_count, guest_name, total_price, status, hold_expires_at, refund_policy_snapshot, created_at)
                select ?, hb.host_user_id, r.id,
                       (select p.id from refund_policies p where p.accommodation_id = a.id and p.active = true limit 1),
                       curdate(), curdate() + interval 1 day, 1, '재고 테스트', 0, 'PENDING',
                       now(6) + interval 1 day, '{}', now(6)
                from rooms r
                join accommodations a on a.id = r.accommodation_id
                join host_businesses hb on hb.id = a.host_business_id
                where r.id = ?
                """, reservationNo, listing.roomId());
        return jdbc.queryForObject("select id from reservations where reservation_no = ?", Long.class, reservationNo);
    }

    private void setInventory(Member host, long roomId, LocalDate start, LocalDate end, Integer totalCount,
                              Integer price, Boolean closed) throws Exception {
        putJson("/api/host/rooms/" + roomId + "/inventories",
                body("startDate", start.toString(), "endDate", end.toString(), "totalCount", totalCount,
                        "price", price, "closed", closed), host.token())
                .andExpect(status().isOk());
    }

    private Map<String, Object> row(long roomId, LocalDate date) {
        return jdbc.queryForMap(
                "select total_count, booked_count, held_count, price, is_closed from room_inventories "
                        + "where room_id = ? and stay_date = ?", roomId, date);
    }

    private String counts(long roomId, LocalDate date) {
        Map<String, Object> row = row(roomId, date);
        return "held=" + row.get("held_count") + ",booked=" + row.get("booked_count");
    }

    private List<String> holdStatuses(long reservationId) {
        return jdbc.queryForList("select status from inventory_holds where reservation_id = ? order by id",
                String.class, reservationId);
    }

    private LocalDateTime expiry() {
        return LocalDateTime.now().plusMinutes(10);
    }

    private JsonNode availability(long accommodationId, LocalDate checkIn, LocalDate checkOut, long roomId)
            throws Exception {
        JsonNode rooms = data(mockMvc.perform(get("/api/accommodations/" + accommodationId + "/availability")
                .param("checkIn", checkIn.toString()).param("checkOut", checkOut.toString()))
                .andExpect(status().isOk()));
        for (JsonNode room : rooms) {
            if (room.get("roomId").asLong() == roomId) {
                return room;
            }
        }
        throw new AssertionError("응답에 없는 객실: " + roomId);
    }

    // ---------- 호스트 재고 설정 ----------

    @Test
    void 호스트가_기간별_수량과_가격을_설정하고_일부만_다시_바꾼다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);

        putJson("/api/host/rooms/" + listing.roomId() + "/inventories",
                body("startDate", day1.toString(), "endDate", day3.toString(), "totalCount", 5, "price", 120000),
                host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].stayDate").value(day1.toString()))
                .andExpect(jsonPath("$.data[0].totalCount").value(5))
                .andExpect(jsonPath("$.data[0].price").value(120000))
                .andExpect(jsonPath("$.data[0].closed").value(false));

        // 하루만 가격을 올리고, 다른 하루는 판매를 막는다. 보내지 않은 값은 그대로다.
        setInventory(host, listing.roomId(), day2, day2, null, 150000, null);
        setInventory(host, listing.roomId(), day3, day3, null, null, true);

        assertThat(row(listing.roomId(), day2)).containsEntry("total_count", 5)
                .containsEntry("price", new BigDecimal("150000.00")).containsEntry("is_closed", false);
        assertThat(row(listing.roomId(), day3)).containsEntry("total_count", 5)
                .containsEntry("price", new BigDecimal("120000.00")).containsEntry("is_closed", true);
        // 재고가 없던 날짜에 가격 없이 수량만 주면 객실 기본가(10만원)로 만든다
        setInventory(host, listing.roomId(), day4, day4, 2, null, null);
        assertThat(row(listing.roomId(), day4)).containsEntry("price", new BigDecimal("100000.00"));
    }

    @Test
    void 다른_호스트와_GUEST는_재고를_설정할_수_없다() throws Exception {
        Member owner = createHost();
        Member other = createHost();
        Member guest = createMember();
        Listing listing = createListedAccommodation(owner, SEOUL);
        Map<String, Object> request = body("startDate", day1.toString(), "endDate", day1.toString(),
                "totalCount", 3, "price", 50000);
        String path = "/api/host/rooms/" + listing.roomId() + "/inventories";

        putJson(path, request, other.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        putJson(path, request, guest.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
        putJson(path, request, null).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from room_inventories where room_id = ?", Integer.class,
                listing.roomId())).isZero();
    }

    @Test
    void 재고_설정_입력_검증() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/host/rooms/" + listing.roomId() + "/inventories";

        putJson(path, body("startDate", day2.toString(), "endDate", day1.toString(), "totalCount", 1), host.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-002"));
        putJson(path, body("startDate", LocalDate.now().minusDays(1).toString(), "endDate", day1.toString(),
                "totalCount", 1), host.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-002"));
        putJson(path, body("startDate", day1.toString(), "endDate", day1.plusDays(400).toString(),
                "totalCount", 1), host.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-003"));
        putJson(path, body("startDate", day1.toString(), "endDate", day1.toString()), host.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-004"));
        putJson(path, body("startDate", day1.toString(), "endDate", day1.toString(), "totalCount", -1),
                host.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-001"));
        putJson("/api/host/rooms/999999999/inventories",
                body("startDate", day1.toString(), "endDate", day1.toString(), "totalCount", 1), host.token())
                .andExpect(status().isNotFound());
    }

    @Test
    void 이미_잡힌_수량보다_적게는_줄일_수_없다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day1, 2, 100000, null);
        long confirmed = newReservationId(listing);
        inventoryService.hold(confirmed, listing.roomId(), day1, day2, expiry());
        inventoryService.confirm(confirmed);
        inventoryService.hold(newReservationId(listing), listing.roomId(), day1, day2, expiry());

        putJson("/api/host/rooms/" + listing.roomId() + "/inventories",
                body("startDate", day1.toString(), "endDate", day1.toString(), "totalCount", 1), host.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY-005"));

        // 가격만 바꿔도 예약·선점 수량은 그대로다
        setInventory(host, listing.roomId(), day1, day1, 2, 130000, null);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=1,booked=1");
    }

    // ---------- 호스트 재고 조회 ----------

    @Test
    void 호스트는_기간별_재고와_예약_선점_수를_조회한다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day2, 3, 100000, null);
        setInventory(host, listing.roomId(), day2, day2, null, null, true);
        long confirmed = newReservationId(listing);
        inventoryService.hold(confirmed, listing.roomId(), day1, day2, expiry());
        inventoryService.confirm(confirmed);
        inventoryService.hold(newReservationId(listing), listing.roomId(), day1, day2, expiry());

        // day3·day4는 재고 행이 없어 결과에 나오지 않는다
        getWith("/api/host/rooms/" + listing.roomId() + "/inventories?startDate=" + day1 + "&endDate=" + day4,
                host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].stayDate").value(day1.toString()))
                .andExpect(jsonPath("$.data[0].totalCount").value(3))
                .andExpect(jsonPath("$.data[0].bookedCount").value(1))
                .andExpect(jsonPath("$.data[0].heldCount").value(1))
                .andExpect(jsonPath("$.data[0].closed").value(false))
                .andExpect(jsonPath("$.data[1].stayDate").value(day2.toString()))
                .andExpect(jsonPath("$.data[1].bookedCount").value(0))
                .andExpect(jsonPath("$.data[1].closed").value(true));
    }

    @Test
    void 재고_조회_입력과_권한_검증() throws Exception {
        Member owner = createHost();
        Member other = createHost();
        Member guest = createMember();
        Listing listing = createListedAccommodation(owner, SEOUL);
        String path = "/api/host/rooms/" + listing.roomId() + "/inventories";
        String range = "?startDate=" + day1 + "&endDate=" + day2;

        getWith(path + "?startDate=" + day2 + "&endDate=" + day1, owner.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-002"));
        getWith(path + "?startDate=" + day1 + "&endDate=" + day1.plusDays(400), owner.token())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-003"));
        getWith(path + "?startDate=" + day1, owner.token()).andExpect(status().isBadRequest());
        // 지난 날짜도 조회할 수 있다
        getWith(path + "?startDate=" + LocalDate.now().minusDays(30) + "&endDate=" + day1, owner.token())
                .andExpect(status().isOk());

        getWith(path + range, other.token())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        getWith(path + range, guest.token())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON-003"));
        getWith(path + range, null).andExpect(status().isUnauthorized());
        getWith("/api/host/rooms/999999999/inventories" + range, owner.token())
                .andExpect(status().isNotFound());
    }

    // ---------- 예약 가능 여부 조회 ----------

    @Test
    void 객실별_예약_가능_여부와_기간_합계_요금을_토큰_없이_조회한다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day3, 1, 100000, null);
        setInventory(host, listing.roomId(), day2, day2, null, 150000, null);

        JsonNode twoNights = availability(listing.accommodationId(), day1, day3, listing.roomId());
        assertThat(twoNights.get("available").asBoolean()).isTrue();
        assertThat(twoNights.get("nights").asInt()).isEqualTo(2);
        assertThat(twoNights.get("totalPrice").asInt()).isEqualTo(250000);
        assertThat(twoNights.get("roomName").asText()).isEqualTo("디럭스");
        assertThat(twoNights.get("maxCapacity").asInt()).isEqualTo(4);

        // 재고 행이 없는 날짜가 끼면 예약할 수 없고 합계도 없다
        JsonNode withMissing = availability(listing.accommodationId(), day3, day4.plusDays(1), listing.roomId());
        assertThat(withMissing.get("available").asBoolean()).isFalse();
        assertThat(withMissing.get("totalPrice").isNull()).isTrue();

        // 판매 중지한 날짜가 끼면 예약할 수 없다
        setInventory(host, listing.roomId(), day3, day3, null, null, true);
        assertThat(availability(listing.accommodationId(), day2, day4, listing.roomId()).get("available").asBoolean())
                .isFalse();

        // 선점되면 그 날짜는 예약할 수 없고, 해제되면 다시 가능하다
        long reservationId = newReservationId(listing);
        inventoryService.hold(reservationId, listing.roomId(), day1, day2, expiry());
        assertThat(availability(listing.accommodationId(), day1, day2, listing.roomId()).get("available").asBoolean())
                .isFalse();
        assertThat(availability(listing.accommodationId(), day2, day3, listing.roomId()).get("available").asBoolean())
                .isTrue();
        inventoryService.release(reservationId);
        assertThat(availability(listing.accommodationId(), day1, day2, listing.roomId()).get("available").asBoolean())
                .isTrue();
    }

    @Test
    void 예약_가능_여부_조회_입력_검증() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/accommodations/" + listing.accommodationId() + "/availability";

        mockMvc.perform(get(path).param("checkIn", day2.toString()).param("checkOut", day2.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-002"));
        mockMvc.perform(get(path).param("checkIn", LocalDate.now().minusDays(1).toString())
                        .param("checkOut", day1.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("checkIn", day1.toString()).param("checkOut", day1.plusDays(31).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVENTORY-003"));
        mockMvc.perform(get(path).param("checkIn", "2026/01/01").param("checkOut", day1.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("checkIn", day1.toString())).andExpect(status().isBadRequest());

        // 공개되지 않은 숙소는 없는 숙소다
        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/listing-status", body("listingStatus", "HIDDEN"),
                host.token()).andExpect(status().isOk());
        mockMvc.perform(get(path).param("checkIn", day1.toString()).param("checkOut", day2.toString()))
                .andExpect(status().isNotFound());
    }

    // ---------- 선점·확정·해제 ----------

    @Test
    void 선점하면_held가_늘고_확정하면_booked로_옮겨지고_해제하면_되돌아간다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day2, 3, 100000, null);
        setInventory(host, listing.roomId(), day2, day2, null, 180000, null);
        long reservationId = newReservationId(listing);

        List<NightPrice> nights = inventoryService.hold(reservationId, listing.roomId(), day1, day3, expiry());

        assertThat(nights).extracting(NightPrice::stayDate).containsExactly(day1, day2);
        assertThat(nights).extracting(n -> n.price().intValue()).containsExactly(100000, 180000);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=1,booked=0");
        assertThat(counts(listing.roomId(), day2)).isEqualTo("held=1,booked=0");
        assertThat(holdStatuses(reservationId)).containsExactly("HELD", "HELD");

        inventoryService.confirm(reservationId);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=1");
        assertThat(counts(listing.roomId(), day2)).isEqualTo("held=0,booked=1");
        assertThat(holdStatuses(reservationId)).containsExactly("CONFIRMED", "CONFIRMED");

        inventoryService.release(reservationId);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=0");
        assertThat(counts(listing.roomId(), day2)).isEqualTo("held=0,booked=0");
        assertThat(holdStatuses(reservationId)).containsExactly("RELEASED", "RELEASED");
    }

    @Test
    void confirm과_release는_두_번_호출해도_결과가_같다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day1, 5, 100000, null);
        long paid = newReservationId(listing);
        long expired = newReservationId(listing);
        inventoryService.hold(paid, listing.roomId(), day1, day2, expiry());
        inventoryService.hold(expired, listing.roomId(), day1, day2, expiry());
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=2,booked=0");

        inventoryService.confirm(paid);
        inventoryService.confirm(paid);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=1,booked=1");

        // 결제 없이 만료된 예약: HELD에서 바로 해제
        inventoryService.release(expired);
        inventoryService.release(expired);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=1");
        // 이미 해제된 선점은 확정되지 않는다
        inventoryService.confirm(expired);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=1");
        assertThat(holdStatuses(expired)).containsExactly("RELEASED");

        inventoryService.release(paid);
        inventoryService.release(paid);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=0");
        // 선점한 적 없는 예약 번호로 호출해도 아무 일도 없다
        inventoryService.confirm(newReservationId(listing));
        inventoryService.release(newReservationId(listing));
    }

    @Test
    void 여러_날짜_중_하루라도_매진이면_전체가_롤백된다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day3, 1, 100000, null);
        long blocker = newReservationId(listing);
        inventoryService.hold(blocker, listing.roomId(), day2, day3, expiry());   // 가운데 날짜를 먼저 잡는다
        long reservationId = newReservationId(listing);

        assertThatThrownBy(() -> inventoryService.hold(reservationId, listing.roomId(), day1, day4, expiry()))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InventoryErrorCode.SOLD_OUT));

        // 첫째 날은 선점에 성공했었지만 함께 되돌려진다
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=0");
        assertThat(counts(listing.roomId(), day2)).isEqualTo("held=1,booked=0");
        assertThat(counts(listing.roomId(), day3)).isEqualTo("held=0,booked=0");
        assertThat(holdStatuses(reservationId)).isEmpty();
    }

    @Test
    void 재고가_없는_날짜와_판매_중지한_날짜는_선점할_수_없다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day1, 1, 100000, null);
        setInventory(host, listing.roomId(), day2, day2, 1, 100000, true);
        setInventory(host, listing.roomId(), day4, day4, 0, 100000, null);

        for (LocalDate[] stay : new LocalDate[][]{{day1, day3}, {day2, day3}, {day3, day4}, {day4, day4.plusDays(1)}}) {
            assertThatThrownBy(() -> inventoryService.hold(newReservationId(listing), listing.roomId(), stay[0], stay[1],
                    expiry()))
                    .isInstanceOfSatisfying(BaseException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(InventoryErrorCode.SOLD_OUT));
        }
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=0,booked=0");
        assertThatThrownBy(() -> inventoryService.hold(newReservationId(listing), listing.roomId(), day2, day1, expiry()))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    void 재고가_1개인_날짜를_동시에_선점하면_한_건만_성공한다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), day1, day2, 1, 100000, null);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            long reservationId = newReservationId(listing);
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    inventoryService.hold(reservationId, listing.roomId(), day1, day3, expiry());
                    return true;
                } catch (BaseException e) {
                    assertThat(e.getErrorCode()).isEqualTo(InventoryErrorCode.SOLD_OUT);
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int success = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                success++;
            }
        }
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(counts(listing.roomId(), day1)).isEqualTo("held=1,booked=0");
        assertThat(counts(listing.roomId(), day2)).isEqualTo("held=1,booked=0");
        assertThat(jdbc.queryForObject(
                "select count(*) from inventory_holds h join room_inventories i on i.id = h.room_inventory_id "
                        + "where i.room_id = ?", Integer.class, listing.roomId())).isEqualTo(2);
    }

    // ---------- 재고 자동 생성 ----------

    @Test
    void 스케줄러는_오늘부터_90일_뒤까지_없는_날짜만_기본가로_만든다() throws Exception {
        Member host = createHost();
        Listing fresh = createListedAccommodation(host, SEOUL);      // 재고가 하나도 없는 객실
        Listing custom = createListedAccommodation(host, SEOUL);     // 호스트가 일부 날짜를 설정한 객실
        setInventory(host, custom.roomId(), day1, day2, 7, 333000, null);
        LocalDate today = LocalDate.now();

        inventoryService.generateUpcoming();

        Map<String, Object> range = jdbc.queryForMap(
                "select count(*) as cnt, min(stay_date) as first, max(stay_date) as last, "
                        + "min(price) as min_price, max(price) as max_price, max(total_count) as total "
                        + "from room_inventories where room_id = ?", fresh.roomId());
        assertThat(((Number) range.get("cnt")).intValue()).isEqualTo(91);
        assertThat(range.get("first").toString()).isEqualTo(today.toString());
        assertThat(range.get("last").toString()).isEqualTo(today.plusDays(90).toString());
        assertThat(range.get("min_price")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(range.get("max_price")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(((Number) range.get("total")).intValue()).isEqualTo(1);

        // 이미 있던 행은 그대로 두고, 새로 만든 행은 마지막 재고의 수량(7)을 이어받는다
        assertThat(row(custom.roomId(), day1)).containsEntry("total_count", 7)
                .containsEntry("price", new BigDecimal("333000.00"));
        assertThat(row(custom.roomId(), day4)).containsEntry("total_count", 7)
                .containsEntry("price", new BigDecimal("100000.00"));
        assertThat(jdbc.queryForObject("select count(*) from room_inventories where room_id = ?", Integer.class,
                custom.roomId())).isEqualTo(91);

        // 다시 돌려도 늘지 않는다
        inventoryService.generateUpcoming();
        assertThat(jdbc.queryForObject("select count(*) from room_inventories where room_id = ?", Integer.class,
                fresh.roomId())).isEqualTo(91);
    }

    @Test
    void 판매_중지한_객실에는_재고를_만들지_않는다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        // 공개 중인 숙소의 마지막 판매 객실은 끌 수 없으므로 숙소를 먼저 숨긴다
        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/listing-status",
                body("listingStatus", "HIDDEN"), host.token()).andExpect(status().isOk());
        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/rooms/" + listing.roomId(),
                body("status", "INACTIVE"), host.token()).andExpect(status().isOk());

        inventoryService.generateUpcoming();

        assertThat(jdbc.queryForObject("select count(*) from room_inventories where room_id = ?", Integer.class,
                listing.roomId())).isZero();
    }
}

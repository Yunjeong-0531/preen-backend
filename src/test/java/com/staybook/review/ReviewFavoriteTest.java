package com.staybook.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.reservation.service.ReservationService;
import com.staybook.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 6단계(review, favorite) 통합 테스트. 실제 MySQL·Redis를 쓴다. */
class ReviewFavoriteTest extends IntegrationTestSupport {

    private static final long SEOUL = 1L;
    private static final String TRACK_ID = "4uLU6hMCjMI75M1A2tKUQC";

    @Autowired
    ReservationService reservationService;

    private final LocalDate today = LocalDate.now();

    private record Stay(Listing listing, long reservationId) {
    }

    /** 예약 → 결제까지 마친 확정 예약. */
    private Stay confirmedStay(Member host, Member guest) throws Exception {
        LocalDate checkIn = today.plusDays(10);
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), checkIn, checkIn, 3, 100000);
        JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
        pay(guest, reservation);
        return new Stay(listing, reservation.get("id").asLong());
    }

    /** 체크아웃이 지나 COMPLETED가 된 예약. */
    private Stay completedStay(Member host, Member guest) throws Exception {
        Stay stay = confirmedStay(host, guest);
        jdbc.update("update reservations set check_in = ?, check_out = ? where id = ?",
                today.minusDays(2), today.minusDays(1), stay.reservationId());
        reservationService.completeStays();
        assertThat(reservationStatus(stay.reservationId())).isEqualTo("COMPLETED");
        return stay;
    }

    private Map<String, Object> reviewBody(long reservationId) {
        return body("reservationId", reservationId, "rating", 5, "content", "조용하고 깨끗했어요.",
                "spotifyTrackId", TRACK_ID,
                "imageUrls", List.of("http://localhost:8080/uploads/r1.jpg", "http://localhost:8080/uploads/r2.jpg"));
    }

    private long writeReview(Member guest, long reservationId) throws Exception {
        return data(postJson("/api/reviews", reviewBody(reservationId), guest.token())
                .andExpect(status().isOk())).get("id").asLong();
    }

    // ---------- 리뷰 작성 ----------

    @Test
    void 체크아웃_후_리뷰를_쓰면_숙소_리뷰_목록에_토큰_없이_보인다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Stay stay = completedStay(host, guest);

        postJson("/api/reviews", reviewBody(stay.reservationId()), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.spotifyTrackId").value(TRACK_ID));

        mockMvc.perform(get("/api/accommodations/" + stay.listing().accommodationId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("조용하고 깨끗했어요."))
                // 작성자 이름은 첫 글자만 보인다 ("테스트회원")
                .andExpect(jsonPath("$.data.content[0].authorName").value("테**"))
                .andExpect(jsonPath("$.data.content[0].spotifyTrackId").value(TRACK_ID))
                .andExpect(jsonPath("$.data.content[0].imageUrls[0]").value("http://localhost:8080/uploads/r1.jpg"))
                .andExpect(jsonPath("$.data.content[0].imageUrls[1]").value("http://localhost:8080/uploads/r2.jpg"))
                .andExpect(jsonPath("$.data.content[0].hostReply").doesNotExist());

        // DB에는 트랙 ID 문자열만 있고 Spotify 트랙 테이블은 없다
        assertThat(jdbc.queryForObject("select spotify_track_id from reviews where reservation_id = ?",
                String.class, stay.reservationId())).isEqualTo(TRACK_ID);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name like '%spotify%'", Integer.class)).isZero();
    }

    @Test
    void 같은_예약으로_두_번째_리뷰는_거부한다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Stay stay = completedStay(host, guest);
        writeReview(guest, stay.reservationId());

        postJson("/api/reviews", reviewBody(stay.reservationId()), guest.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW-004"));

        assertThat(jdbc.queryForObject("select count(*) from reviews where reservation_id = ?", Integer.class,
                stay.reservationId())).isEqualTo(1);
    }

    @Test
    void 투숙이_끝나지_않았거나_남의_예약이면_리뷰를_쓸_수_없다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        Stay confirmed = confirmedStay(host, guest);

        postJson("/api/reviews", reviewBody(confirmed.reservationId()), guest.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW-003"));

        Stay completed = completedStay(host, guest);
        postJson("/api/reviews", reviewBody(completed.reservationId()), stranger.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW-002"));
        postJson("/api/reviews", reviewBody(completed.reservationId()), host.token())
                .andExpect(status().isForbidden());
        postJson("/api/reviews", reviewBody(completed.reservationId())).andExpect(status().isUnauthorized());
        postJson("/api/reviews", reviewBody(999_999_999L), guest.token()).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from reviews where reservation_id in (?, ?)", Integer.class,
                confirmed.reservationId(), completed.reservationId())).isZero();
    }

    @Test
    void 리뷰_입력_검증() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Stay stay = completedStay(host, guest);

        for (Object[] invalid : new Object[][]{{"rating", 6}, {"rating", 0}, {"content", " "},
                {"spotifyTrackId", "not a track id"}, {"spotifyTrackId", "https://open.spotify.com/track/x"}}) {
            Map<String, Object> request = reviewBody(stay.reservationId());
            request.put((String) invalid[0], invalid[1]);
            postJson("/api/reviews", request, guest.token())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-001"));
        }
        // 음악과 사진은 없어도 된다
        Map<String, Object> minimal = body("reservationId", stay.reservationId(), "rating", 3, "content", "무난");
        postJson("/api/reviews", minimal, guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spotifyTrackId").doesNotExist())
                .andExpect(jsonPath("$.data.imageUrls.length()").value(0));
    }

    // ---------- 수정·삭제 ----------

    @Test
    void 리뷰를_수정하면_보낸_항목만_바뀌고_음악을_뗄_수_있다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        Stay stay = completedStay(host, guest);
        long reviewId = writeReview(guest, stay.reservationId());

        patchJson("/api/reviews/" + reviewId, body("rating", 2), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(2))
                .andExpect(jsonPath("$.data.content").value("조용하고 깨끗했어요."))
                .andExpect(jsonPath("$.data.spotifyTrackId").value(TRACK_ID))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2));
        patchJson("/api/reviews/" + reviewId,
                body("content", "다시 생각해 보니 별로", "removeSpotifyTrack", true,
                        "imageUrls", List.of("http://localhost:8080/uploads/r3.jpg")), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("다시 생각해 보니 별로"))
                .andExpect(jsonPath("$.data.spotifyTrackId").doesNotExist())
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1));

        patchJson("/api/reviews/" + reviewId, body("rating", 5), stranger.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW-005"));
        patchJson("/api/reviews/" + reviewId, body("rating", 9), guest.token()).andExpect(status().isBadRequest());
        patchJson("/api/reviews/999999999", body("rating", 5), guest.token()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select rating from reviews where id = ?", Integer.class, reviewId))
                .isEqualTo(2);
    }

    @Test
    void 리뷰를_지우면_목록에서_사라지고_같은_예약으로_다시_쓸_수_있다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        Stay stay = completedStay(host, guest);
        long reviewId = writeReview(guest, stay.reservationId());

        mockMvc.perform(withToken(delete("/api/reviews/" + reviewId), stranger.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reviews/" + reviewId)).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(delete("/api/reviews/" + reviewId), guest.token())).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from review_images where review_id = ?", Integer.class,
                reviewId)).isZero();
        mockMvc.perform(get("/api/accommodations/" + stay.listing().accommodationId() + "/reviews"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(withToken(delete("/api/reviews/" + reviewId), guest.token()))
                .andExpect(status().isNotFound());
        writeReview(guest, stay.reservationId());
    }

    // ---------- 호스트 답글·목록 ----------

    @Test
    void 호스트는_자기_숙소의_리뷰에만_답글을_달_수_있다() throws Exception {
        Member host = createHost();
        Member otherHost = createHost();
        Member guest = createMember();
        Stay stay = completedStay(host, guest);
        long reviewId = writeReview(guest, stay.reservationId());
        String path = "/api/host/reviews/" + reviewId + "/reply";

        postJson(path, body("reply", "남의 숙소에 답글"), otherHost.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOMMODATION-002"));
        postJson(path, body("reply", "게스트의 답글"), guest.token())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
        postJson(path, body("reply", " "), host.token()).andExpect(status().isBadRequest());
        postJson("/api/host/reviews/999999999/reply", body("reply", "없는 리뷰"), host.token())
                .andExpect(status().isNotFound());

        postJson(path, body("reply", "찾아 주셔서 감사합니다."), host.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostReply").value("찾아 주셔서 감사합니다."))
                .andExpect(jsonPath("$.data.hostRepliedAt").exists());
        // 다시 쓰면 덮어쓴다
        postJson(path, body("reply", "또 오세요."), host.token()).andExpect(status().isOk());

        mockMvc.perform(get("/api/accommodations/" + stay.listing().accommodationId() + "/reviews"))
                .andExpect(jsonPath("$.data.content[0].hostReply").value("또 오세요."));
    }

    @Test
    void 리뷰_목록은_최근_작성순이고_숨긴_리뷰와_공개되지_않은_숙소는_나오지_않는다() throws Exception {
        Member host = createHost();
        Member first = createMember();
        Member second = createMember();
        LocalDate checkIn = today.plusDays(10);
        Listing listing = createListedAccommodation(host, SEOUL);
        setInventory(host, listing.roomId(), checkIn, checkIn, 3, 100000);
        long[] reviewIds = new long[2];
        Member[] guests = {first, second};
        for (int i = 0; i < 2; i++) {
            JsonNode reservation = reserveOk(guests[i], listing.roomId(), checkIn, checkIn.plusDays(1), 2);
            pay(guests[i], reservation);
            jdbc.update("update reservations set check_in = ?, check_out = ? where id = ?",
                    today.minusDays(2), today.minusDays(1), reservation.get("id").asLong());
            reservationService.completeStays();
            reviewIds[i] = writeReview(guests[i], reservation.get("id").asLong());
        }
        String path = "/api/accommodations/" + listing.accommodationId() + "/reviews";

        mockMvc.perform(get(path))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(reviewIds[1]))
                .andExpect(jsonPath("$.data.content[1].id").value(reviewIds[0]));
        mockMvc.perform(get(path).param("size", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        jdbc.update("update reviews set status = 'HIDDEN' where id = ?", reviewIds[1]);
        mockMvc.perform(get(path))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(reviewIds[0]));

        patchJson("/api/host/accommodations/" + listing.accommodationId(), body("listingStatus", "HIDDEN"),
                host.token()).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVIEW-006"));
    }

    // ---------- 찜 ----------

    @Test
    void 찜은_다시_눌러도_한_번만_저장되고_없는_찜을_지워도_성공이다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Listing listing = createListedAccommodation(host, SEOUL);
        String path = "/api/favorites/" + listing.accommodationId();

        mockMvc.perform(withToken(post(path), guest.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(withToken(post(path), guest.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(jdbc.queryForObject("select count(*) from favorites where user_id = ? and accommodation_id = ?",
                Integer.class, guest.userId(), listing.accommodationId())).isEqualTo(1);

        mockMvc.perform(withToken(delete(path), guest.token())).andExpect(status().isOk());
        mockMvc.perform(withToken(delete(path), guest.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(jdbc.queryForObject("select count(*) from favorites where user_id = ?", Integer.class,
                guest.userId())).isZero();

        mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(post("/api/favorites/999999999"), guest.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAVORITE-001"));
    }

    @Test
    void 내_찜_목록은_최근에_찜한_순이고_남의_찜과_숨겨진_숙소는_나오지_않는다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member other = createMember();
        Listing first = createListedAccommodation(host, SEOUL);
        Listing second = createListedAccommodation(host, SEOUL);
        Listing othersPick = createListedAccommodation(host, SEOUL);
        mockMvc.perform(withToken(post("/api/favorites/" + first.accommodationId()), guest.token()))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/favorites/" + second.accommodationId()), guest.token()))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/favorites/" + othersPick.accommodationId()), other.token()))
                .andExpect(status().isOk());

        getWith("/api/favorites/me", guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].accommodation.id").value(second.accommodationId()))
                .andExpect(jsonPath("$.data.content[1].accommodation.id").value(first.accommodationId()))
                .andExpect(jsonPath("$.data.content[0].accommodation.minNightlyPrice").value(100000))
                .andExpect(jsonPath("$.data.content[0].accommodation.thumbnailUrl").exists())
                .andExpect(jsonPath("$.data.content[0].favoritedAt").exists());
        getWith("/api/favorites/me", null).andExpect(status().isUnauthorized());

        // 찜한 뒤 호스트가 숨긴 숙소는 목록에서 빠진다
        patchJson("/api/host/accommodations/" + second.accommodationId(), body("listingStatus", "HIDDEN"),
                host.token()).andExpect(status().isOk());
        getWith("/api/favorites/me", guest.token())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].accommodation.id").value(first.accommodationId()));
        // 미성년 회원도 찜은 할 수 있다 (예약만 성인 전용)
        String email = uniqueEmail();
        signUp(email, today.minusYears(15));
        mockMvc.perform(withToken(post("/api/favorites/" + first.accommodationId()),
                login(email, PASSWORD).get("accessToken").asText())).andExpect(status().isOk());
    }
}

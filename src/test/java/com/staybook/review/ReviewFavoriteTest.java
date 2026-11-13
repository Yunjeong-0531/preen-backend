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
        return reviewBody(reservationId, 5, TRACK_ID);
    }

    private Map<String, Object> reviewBody(long reservationId, int rating, String trackId) {
        return body("reservationId", reservationId, "rating", rating, "spotifyTrackId", trackId);
    }

    private long writeReview(Member guest, long reservationId) throws Exception {
        return data(postJson("/api/reviews", reviewBody(reservationId), guest.token())
                .andExpect(status().isOk())).get("id").asLong();
    }

    /** 한 숙소에 손님마다 예약 → 결제 → 투숙 완료 → 리뷰를 남긴다. 작성한 리뷰 ID를 순서대로 돌려준다. */
    private long[] writeReviews(Member host, Listing listing, int[] ratings, String[] trackIds) throws Exception {
        LocalDate checkIn = today.plusDays(10);
        setInventory(host, listing.roomId(), checkIn, checkIn, ratings.length + 1, 100000);
        long[] reviewIds = new long[ratings.length];
        for (int i = 0; i < ratings.length; i++) {
            Member guest = createMember();
            JsonNode reservation = reserveOk(guest, listing.roomId(), checkIn, checkIn.plusDays(1), 2);
            pay(guest, reservation);
            jdbc.update("update reservations set check_in = ?, check_out = ? where id = ?",
                    today.minusDays(2), today.minusDays(1), reservation.get("id").asLong());
            reservationService.completeStays();
            reviewIds[i] = data(postJson("/api/reviews",
                    reviewBody(reservation.get("id").asLong(), ratings[i], trackIds[i]), guest.token())
                    .andExpect(status().isOk())).get("id").asLong();
        }
        return reviewIds;
    }

    private List<Long> reviewIds(String query) throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        data(mockMvc.perform(get(query)).andExpect(status().isOk())).get("content")
                .forEach(r -> ids.add(r.get("id").asLong()));
        return ids;
    }

    // ---------- 리뷰 작성 ----------

    @Test
    void 체크아웃_후_별점과_음악으로_리뷰를_쓰면_숙소_리뷰_목록에_토큰_없이_보인다() throws Exception {
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
                // 작성자 이름은 첫 글자만 보인다 ("테스트회원")
                .andExpect(jsonPath("$.data.content[0].authorName").value("테**"))
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[0].spotifyTrackId").value(TRACK_ID))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                // 글, 사진, 호스트 답글은 없다
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].imageUrls").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].hostReply").doesNotExist());

        // DB에는 트랙 ID 문자열만 있고 Spotify 트랙 테이블은 없다
        assertThat(jdbc.queryForObject("select spotify_track_id from reviews where reservation_id = ?",
                String.class, stay.reservationId())).isEqualTo(TRACK_ID);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name like '%spotify%'", Integer.class)).isZero();
    }

    @Test
    void 리뷰_글_사진_답글_저장소는_스키마에_없다() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = database() and table_name = 'review_images'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_schema = database() and table_name = 'reviews'", String.class))
                .doesNotContain("content", "host_reply", "host_replied_at")
                .contains("rating", "spotify_track_id");
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns "
                + "where table_schema = database() and table_name = 'reviews' and column_name = 'spotify_track_id'",
                String.class)).isEqualTo("NO");
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

        for (Object[] invalid : new Object[][]{{"rating", 6}, {"rating", 0}, {"rating", null},
                {"spotifyTrackId", null}, {"spotifyTrackId", "not a track id"},
                {"spotifyTrackId", "https://open.spotify.com/track/x"}}) {
            Map<String, Object> request = reviewBody(stay.reservationId());
            request.put((String) invalid[0], invalid[1]);
            postJson("/api/reviews", request, guest.token())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-001"));
        }
        // 음악 없이 별점만으로는 쓸 수 없다
        postJson("/api/reviews", body("reservationId", stay.reservationId(), "rating", 3), guest.token())
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from reviews where reservation_id = ?", Integer.class,
                stay.reservationId())).isZero();

        // 예전 필드(글, 사진)를 보내도 저장하지 않고 응답에도 없다
        Map<String, Object> withOldFields = reviewBody(stay.reservationId());
        withOldFields.put("content", "글은 이제 저장하지 않는다");
        withOldFields.put("imageUrls", List.of("http://localhost:8080/uploads/r1.jpg"));
        postJson("/api/reviews", withOldFields, guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.imageUrls").doesNotExist());
    }

    // ---------- 수정·삭제 ----------

    @Test
    void 리뷰를_수정하면_보낸_항목만_바뀌고_음악은_다른_곡으로만_바꿀_수_있다() throws Exception {
        Member host = createHost();
        Member guest = createMember();
        Member stranger = createMember();
        Stay stay = completedStay(host, guest);
        long reviewId = writeReview(guest, stay.reservationId());
        String otherTrack = "7qiZfU4dY1lWllzX7mPBI3";

        patchJson("/api/reviews/" + reviewId, body("rating", 2), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(2))
                .andExpect(jsonPath("$.data.spotifyTrackId").value(TRACK_ID));
        patchJson("/api/reviews/" + reviewId, body("spotifyTrackId", otherTrack), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(2))
                .andExpect(jsonPath("$.data.spotifyTrackId").value(otherTrack));
        // 예전의 음악 떼기 요청은 무시되고 곡이 그대로 남는다
        patchJson("/api/reviews/" + reviewId, body("removeSpotifyTrack", true), guest.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spotifyTrackId").value(otherTrack));
        patchJson("/api/reviews/" + reviewId, body("spotifyTrackId", "bad"), guest.token())
                .andExpect(status().isBadRequest());

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

        mockMvc.perform(get("/api/accommodations/" + stay.listing().accommodationId() + "/reviews"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(withToken(delete("/api/reviews/" + reviewId), guest.token()))
                .andExpect(status().isNotFound());
        writeReview(guest, stay.reservationId());
    }

    // ---------- 목록 ----------

    @Test
    void 리뷰_목록은_별점순으로_정렬하고_별점으로_거를_수_있다() throws Exception {
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        // 작성 순서대로 r0(3점), r1(5점), r2(1점), r3(3점)
        long[] r = writeReviews(host, listing, new int[]{3, 5, 1, 3},
                new String[]{TRACK_ID, TRACK_ID, TRACK_ID, TRACK_ID});
        String path = "/api/accommodations/" + listing.accommodationId() + "/reviews";

        // 기본은 최근 작성순
        assertThat(reviewIds(path)).containsExactly(r[3], r[2], r[1], r[0]);
        assertThat(reviewIds(path + "?sort=LATEST")).containsExactly(r[3], r[2], r[1], r[0]);
        // 별점순, 같은 별점끼리는 최근 작성순
        assertThat(reviewIds(path + "?sort=RATING_DESC")).containsExactly(r[1], r[3], r[0], r[2]);
        assertThat(reviewIds(path + "?sort=RATING_ASC")).containsExactly(r[2], r[3], r[0], r[1]);

        // 별점으로 거르기, 정렬과 함께 쓸 수 있다
        assertThat(reviewIds(path + "?rating=3")).containsExactly(r[3], r[0]);
        assertThat(reviewIds(path + "?rating=5&sort=RATING_ASC")).containsExactly(r[1]);
        assertThat(reviewIds(path + "?rating=4")).isEmpty();
        mockMvc.perform(get(path).param("rating", "3"))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // 잘못된 값은 400
        mockMvc.perform(get(path).param("rating", "6")).andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("rating", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("sort", "POPULAR")).andExpect(status().isBadRequest());
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

        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/listing-status", body("listingStatus", "HIDDEN"),
                host.token()).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVIEW-006"));
    }

    // ---------- 숙소별 트랙 목록 ----------

    @Test
    void 숙소_트랙_목록은_곡별로_묶이고_최근에_붙은_곡부터_나오며_숨긴_리뷰는_빠진다() throws Exception {
        String trackA = TRACK_ID;
        String trackB = "7qiZfU4dY1lWllzX7mPBI3";
        Member host = createHost();
        Listing listing = createListedAccommodation(host, SEOUL);
        // 리뷰 순서: A, B, A
        long[] reviewIds = writeReviews(host, listing, new int[]{4, 4, 4}, new String[]{trackA, trackB, trackA});
        String path = "/api/accommodations/" + listing.accommodationId() + "/tracks";

        // 토큰 없이 조회. 같은 곡은 한 번만, 가장 최근 리뷰에 붙은 A가 먼저
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].spotifyTrackId").value(trackA))
                .andExpect(jsonPath("$.data[0].reviewCount").value(2))
                .andExpect(jsonPath("$.data[0].lastAddedAt").exists())
                .andExpect(jsonPath("$.data[1].spotifyTrackId").value(trackB))
                .andExpect(jsonPath("$.data[1].reviewCount").value(1));

        // 세 번째 리뷰(A)를 숨기면 A는 첫 리뷰 하나만 남아 B 뒤로 간다
        jdbc.update("update reviews set status = 'HIDDEN' where id = ?", reviewIds[2]);
        mockMvc.perform(get(path))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].spotifyTrackId").value(trackB))
                .andExpect(jsonPath("$.data[1].spotifyTrackId").value(trackA))
                .andExpect(jsonPath("$.data[1].reviewCount").value(1));

        // 공개되지 않은 숙소와 없는 숙소는 404
        patchJson("/api/host/accommodations/" + listing.accommodationId() + "/listing-status",
                body("listingStatus", "HIDDEN"), host.token()).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVIEW-006"));
        mockMvc.perform(get("/api/accommodations/999999999/tracks")).andExpect(status().isNotFound());
    }

    @Test
    void 리뷰가_없으면_트랙_목록은_빈_목록이다() throws Exception {
        Listing listing = createListedAccommodation(createHost(), SEOUL);
        mockMvc.perform(get("/api/accommodations/" + listing.accommodationId() + "/tracks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
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
        patchJson("/api/host/accommodations/" + second.accommodationId() + "/listing-status", body("listingStatus", "HIDDEN"),
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

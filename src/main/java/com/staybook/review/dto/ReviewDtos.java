package com.staybook.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

/** review 요청·응답 모음. 리뷰는 별점과 음악(Spotify 트랙)만 남긴다. */
public final class ReviewDtos {

    /** Spotify 트랙 ID 형식 (영문·숫자 22자). */
    private static final String TRACK_ID_PATTERN = "^[A-Za-z0-9]{22}$";
    private static final String TRACK_ID_MESSAGE = "Spotify 트랙 ID 형식이 올바르지 않습니다.";

    private ReviewDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "예약을 선택해 주세요.") Long reservationId,
            @NotNull(message = "평점을 선택해 주세요.") @Min(value = 1, message = "평점은 1~5점입니다.")
            @Max(value = 5, message = "평점은 1~5점입니다.") Integer rating,
            @NotNull(message = "음악을 선택해 주세요.")
            @Pattern(regexp = TRACK_ID_PATTERN, message = TRACK_ID_MESSAGE) String spotifyTrackId
    ) {
    }

    /** 보내지 않은(null) 항목은 바꾸지 않는다. 음악은 필수라 뗄 수 없고 다른 곡으로만 바꿀 수 있다. */
    public record UpdateRequest(
            @Min(value = 1, message = "평점은 1~5점입니다.")
            @Max(value = 5, message = "평점은 1~5점입니다.") Integer rating,
            @Pattern(regexp = TRACK_ID_PATTERN, message = TRACK_ID_MESSAGE) String spotifyTrackId
    ) {
    }

    /** 리뷰 목록 정렬. 같은 별점끼리는 최근 작성순이다. */
    public enum ReviewSort {
        LATEST,
        RATING_DESC,
        RATING_ASC
    }

    /**
     * @param authorName     이름의 첫 글자만 보이게 가린 작성자 이름
     * @param spotifyTrackId 프론트가 공식 임베드 플레이어(open.spotify.com/embed/track/{id})로 표시한다
     */
    public record ReviewResponse(
            Long id,
            Long accommodationId,
            String authorName,
            int rating,
            String spotifyTrackId,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 숙소별 트랙 목록의 한 곡. 같은 곡이 여러 리뷰에 붙었으면 한 번만 나온다.
     *
     * @param spotifyTrackId 프론트가 공식 임베드 플레이어(open.spotify.com/embed/track/{id})로 표시한다
     * @param reviewCount    이 곡을 붙인 노출 리뷰 수
     * @param lastAddedAt    이 곡이 붙은 가장 최근 리뷰의 작성 시각
     */
    public record TrackResponse(
            String spotifyTrackId,
            long reviewCount,
            LocalDateTime lastAddedAt
    ) {
    }
}

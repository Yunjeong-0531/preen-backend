package com.staybook.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** review 요청·응답 모음. */
public final class ReviewDtos {

    /** Spotify 트랙 ID 형식 (영문·숫자 22자). */
    private static final String TRACK_ID_PATTERN = "^[A-Za-z0-9]{22}$";
    private static final String TRACK_ID_MESSAGE = "Spotify 트랙 ID 형식이 올바르지 않습니다.";

    private ReviewDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "예약을 선택해 주세요.") Long reservationId,
            @NotNull @Min(value = 1, message = "평점은 1~5점입니다.")
            @Max(value = 5, message = "평점은 1~5점입니다.") Integer rating,
            @NotBlank(message = "리뷰 내용을 입력해 주세요.") @Size(max = 2000) String content,
            @Pattern(regexp = TRACK_ID_PATTERN, message = TRACK_ID_MESSAGE) String spotifyTrackId,
            @Size(max = 10, message = "사진은 10장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls
    ) {
    }

    /**
     * 보내지 않은(null) 항목은 바꾸지 않는다. imageUrls는 보내면 통째로 교체한다.
     * 음악을 떼려면 removeSpotifyTrack을 true로 보낸다.
     */
    public record UpdateRequest(
            @Min(value = 1, message = "평점은 1~5점입니다.")
            @Max(value = 5, message = "평점은 1~5점입니다.") Integer rating,
            @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*", message = "리뷰 내용을 입력해 주세요.") String content,
            @Pattern(regexp = TRACK_ID_PATTERN, message = TRACK_ID_MESSAGE) String spotifyTrackId,
            Boolean removeSpotifyTrack,
            @Size(max = 10, message = "사진은 10장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls
    ) {
    }

    public record ReplyRequest(@NotBlank(message = "답글 내용을 입력해 주세요.") @Size(max = 2000) String reply) {
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
            String content,
            String spotifyTrackId,
            List<String> imageUrls,
            String hostReply,
            LocalDateTime hostRepliedAt,
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

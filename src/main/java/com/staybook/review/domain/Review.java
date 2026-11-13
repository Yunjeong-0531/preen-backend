package com.staybook.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 투숙 리뷰. 예약 1건당 1개(reservation_id 유니크). 별점과 음악만 남긴다.
 * 음악은 Spotify 트랙 ID 문자열만 저장하고 제목·아티스트·커버 같은 메타데이터는 저장하지 않는다.
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    public enum Status {
        VISIBLE,
        HIDDEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long accommodationId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String spotifyTrackId;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int rating;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Review create(Long reservationId, Long accommodationId, Long userId, String spotifyTrackId,
                                int rating, LocalDateTime now) {
        Review review = new Review();
        review.reservationId = reservationId;
        review.accommodationId = accommodationId;
        review.userId = userId;
        review.spotifyTrackId = spotifyTrackId;
        review.rating = rating;
        review.status = Status.VISIBLE;
        review.createdAt = now;
        return review;
    }

    public void changeRating(int rating) {
        this.rating = rating;
    }

    public void changeSpotifyTrackId(String spotifyTrackId) {
        this.spotifyTrackId = spotifyTrackId;
    }
}

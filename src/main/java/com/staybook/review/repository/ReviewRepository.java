package com.staybook.review.repository;

import com.staybook.review.domain.Review;
import com.staybook.review.dto.ReviewDtos.TrackResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByReservationId(Long reservationId);

    Page<Review> findByAccommodationIdAndStatus(Long accommodationId, Review.Status status, Pageable pageable);

    Page<Review> findByAccommodationIdAndStatusAndRating(Long accommodationId, Review.Status status, int rating,
                                                         Pageable pageable);

    /** 숙소의 노출 리뷰에 붙은 트랙을 곡별로 묶는다. 가장 최근 리뷰에 붙은 곡이 앞에 온다. */
    @Query("""
            select new com.staybook.review.dto.ReviewDtos$TrackResponse(
                r.spotifyTrackId, count(r), max(r.createdAt))
            from Review r
            where r.accommodationId = :accommodationId
              and r.status = :status
              and r.spotifyTrackId is not null
            group by r.spotifyTrackId
            order by max(r.id) desc
            """)
    List<TrackResponse> findTracks(@Param("accommodationId") Long accommodationId,
                                   @Param("status") Review.Status status, Pageable pageable);
}

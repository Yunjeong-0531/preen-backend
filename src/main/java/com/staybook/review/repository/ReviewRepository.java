package com.staybook.review.repository;

import com.staybook.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByReservationId(Long reservationId);

    Page<Review> findByAccommodationIdAndStatus(Long accommodationId, Review.Status status, Pageable pageable);
}

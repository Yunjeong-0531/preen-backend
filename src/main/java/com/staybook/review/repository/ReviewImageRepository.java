package com.staybook.review.repository;

import com.staybook.review.domain.ReviewImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewIdInOrderBySortOrderAsc(Collection<Long> reviewIds);

    @Modifying
    @Query("delete from ReviewImage i where i.reviewId = :reviewId")
    void deleteAllByReviewId(@Param("reviewId") Long reviewId);
}

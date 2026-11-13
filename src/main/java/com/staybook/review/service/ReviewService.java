package com.staybook.review.service;

import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.response.PageResponse;
import com.staybook.reservation.dto.ReservationDtos.StayInfo;
import com.staybook.reservation.service.ReservationService;
import com.staybook.review.domain.Review;
import com.staybook.review.dto.ReviewDtos.CreateRequest;
import com.staybook.review.dto.ReviewDtos.ReviewResponse;
import com.staybook.review.dto.ReviewDtos.ReviewSort;
import com.staybook.review.dto.ReviewDtos.TrackResponse;
import com.staybook.review.dto.ReviewDtos.UpdateRequest;
import com.staybook.review.exception.ReviewErrorCode;
import com.staybook.review.repository.ReviewRepository;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰는 별점과 음악(Spotify 트랙)만 남긴다. 글, 사진, 호스트 답글은 없다. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TRACKS = 50;

    private final ReviewRepository reviewRepository;
    private final ReservationService reservationService;
    private final AccommodationQueryService accommodationQueryService;
    private final UserService userService;
    private final Clock clock;

    /**
     * 숙소 리뷰 목록. 노출(VISIBLE) 상태만 나온다.
     *
     * @param sort   정렬. null이면 최근 작성순
     * @param rating 이 별점(1~5)인 리뷰만. null이면 전체
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getForAccommodation(Long accommodationId, ReviewSort sort, Integer rating,
                                                            int page, int size) {
        if (!accommodationQueryService.existsListed(accommodationId)) {
            throw new BaseException(ReviewErrorCode.ACCOMMODATION_NOT_FOUND);
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                toSort(sort));
        Page<Review> reviews = rating == null
                ? reviewRepository.findByAccommodationIdAndStatus(accommodationId, Review.Status.VISIBLE, pageable)
                : reviewRepository.findByAccommodationIdAndStatusAndRating(accommodationId, Review.Status.VISIBLE,
                rating, pageable);
        return PageResponse.from(reviews, this::toResponse);
    }

    /** 숙소별 트랙 목록. 노출 리뷰에 붙은 곡을 중복 없이, 최근에 붙은 곡부터 최대 MAX_TRACKS곡. */
    @Transactional(readOnly = true)
    public List<TrackResponse> getTracksForAccommodation(Long accommodationId) {
        if (!accommodationQueryService.existsListed(accommodationId)) {
            throw new BaseException(ReviewErrorCode.ACCOMMODATION_NOT_FOUND);
        }
        return reviewRepository.findTracks(accommodationId, Review.Status.VISIBLE, PageRequest.of(0, MAX_TRACKS));
    }

    /** 작성 조건: 본인 예약, 예약 상태 COMPLETED, 예약당 1건. 별점과 음악은 필수다. */
    @Transactional
    public ReviewResponse create(Long userId, CreateRequest request) {
        StayInfo stay = reservationService.getStayInfo(request.reservationId());
        if (!stay.userId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_MY_RESERVATION);
        }
        if (!stay.completed()) {
            throw new BaseException(ReviewErrorCode.STAY_NOT_COMPLETED);
        }
        if (reviewRepository.existsByReservationId(stay.reservationId())) {
            throw new BaseException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        try {
            return toResponse(reviewRepository.saveAndFlush(Review.create(stay.reservationId(),
                    stay.accommodationId(), userId, request.spotifyTrackId(), request.rating(),
                    LocalDateTime.now(clock))));
        } catch (DataIntegrityViolationException e) {
            // 같은 예약으로 동시에 작성한 경우 유니크 제약에 걸린다
            throw new BaseException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, UpdateRequest request) {
        Review review = loadMine(reviewId, userId);
        if (request.rating() != null) {
            review.changeRating(request.rating());
        }
        if (request.spotifyTrackId() != null) {
            review.changeSpotifyTrackId(request.spotifyTrackId());
        }
        return toResponse(review);
    }

    /** 리뷰를 지운다. 지운 뒤에는 같은 예약으로 다시 쓸 수 있다. */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        reviewRepository.delete(loadMine(reviewId, userId));
    }

    private Review loadMine(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_MY_REVIEW);
        }
        return review;
    }

    /** 같은 별점끼리는 최근 작성순으로 둔다. */
    private static Sort toSort(ReviewSort sort) {
        Sort latest = Sort.by(Sort.Direction.DESC, "id");
        if (sort == null || sort == ReviewSort.LATEST) {
            return latest;
        }
        Sort.Direction direction = sort == ReviewSort.RATING_DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, "rating").and(latest);
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(review.getId(), review.getAccommodationId(),
                mask(userService.findName(review.getUserId()).orElse("")), review.getRating(),
                review.getSpotifyTrackId(), review.getCreatedAt());
    }

    /** 이름의 첫 글자만 남기고 가린다. 예: 김철수 → 김** */
    static String mask(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "*";
        }
        int first = trimmed.offsetByCodePoints(0, 1);
        int rest = trimmed.codePointCount(first, trimmed.length());
        return trimmed.substring(0, first) + "*".repeat(Math.max(1, Math.min(rest, 2)));
    }
}

package com.staybook.review.service;

import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.response.PageResponse;
import com.staybook.reservation.dto.ReservationDtos.StayInfo;
import com.staybook.reservation.service.ReservationService;
import com.staybook.review.domain.Review;
import com.staybook.review.domain.ReviewImage;
import com.staybook.review.dto.ReviewDtos.CreateRequest;
import com.staybook.review.dto.ReviewDtos.ReplyRequest;
import com.staybook.review.dto.ReviewDtos.ReviewResponse;
import com.staybook.review.dto.ReviewDtos.UpdateRequest;
import com.staybook.review.exception.ReviewErrorCode;
import com.staybook.review.repository.ReviewImageRepository;
import com.staybook.review.repository.ReviewRepository;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReservationService reservationService;
    private final AccommodationQueryService accommodationQueryService;
    private final UserService userService;
    private final Clock clock;

    /** 숙소 리뷰 목록, 최근 작성순. 노출(VISIBLE) 상태만 나온다. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getForAccommodation(Long accommodationId, int page, int size) {
        if (!accommodationQueryService.existsListed(accommodationId)) {
            throw new BaseException(ReviewErrorCode.ACCOMMODATION_NOT_FOUND);
        }
        Page<Review> reviews = reviewRepository.findByAccommodationIdAndStatus(accommodationId, Review.Status.VISIBLE,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = reviews.getContent().stream().map(Review::getId).toList();
        Map<Long, List<String>> images = ids.isEmpty() ? Map.of()
                : reviewImageRepository.findByReviewIdInOrderBySortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(ReviewImage::getReviewId,
                        Collectors.mapping(ReviewImage::getImageUrl, Collectors.toList())));
        return PageResponse.from(reviews, r -> toResponse(r, images.getOrDefault(r.getId(), List.of())));
    }

    /** 작성 조건: 본인 예약, 예약 상태 COMPLETED, 예약당 1건. */
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
        Review review;
        try {
            review = reviewRepository.saveAndFlush(Review.create(stay.reservationId(), stay.accommodationId(),
                    userId, request.spotifyTrackId(), request.rating(), request.content().trim(),
                    LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 같은 예약으로 동시에 작성한 경우 유니크 제약에 걸린다
            throw new BaseException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        return toResponse(review, replaceImages(review.getId(), request.imageUrls()));
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, UpdateRequest request) {
        Review review = loadMine(reviewId, userId);
        if (request.rating() != null) {
            review.changeRating(request.rating());
        }
        if (request.content() != null) {
            review.changeContent(request.content().trim());
        }
        if (Boolean.TRUE.equals(request.removeSpotifyTrack())) {
            review.changeSpotifyTrackId(null);
        } else if (request.spotifyTrackId() != null) {
            review.changeSpotifyTrackId(request.spotifyTrackId());
        }
        List<String> imageUrls = request.imageUrls() != null
                ? replaceImages(reviewId, request.imageUrls())
                : reviewImageRepository.findByReviewIdInOrderBySortOrderAsc(List.of(reviewId)).stream()
                .map(ReviewImage::getImageUrl).toList();
        return toResponse(review, imageUrls);
    }

    /** 리뷰와 사진을 지운다. 지운 뒤에는 같은 예약으로 다시 쓸 수 있다. */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = loadMine(reviewId, userId);
        reviewImageRepository.deleteAllByReviewId(reviewId);
        reviewRepository.delete(review);
    }

    /** 호스트 답글. 리뷰가 달린 숙소가 본인 소유인지 검사한다. 다시 쓰면 덮어쓴다. */
    @Transactional
    public ReviewResponse reply(Long hostUserId, Long reviewId, ReplyRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));
        accommodationQueryService.assertAccommodationOwner(review.getAccommodationId(), hostUserId);
        review.reply(request.reply().trim(), LocalDateTime.now(clock));
        return toResponse(review, reviewImageRepository.findByReviewIdInOrderBySortOrderAsc(List.of(reviewId))
                .stream().map(ReviewImage::getImageUrl).toList());
    }

    private Review loadMine(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BaseException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw new BaseException(ReviewErrorCode.NOT_MY_REVIEW);
        }
        return review;
    }

    private List<String> replaceImages(Long reviewId, List<String> imageUrls) {
        reviewImageRepository.deleteAllByReviewId(reviewId);
        if (imageUrls == null) {
            return List.of();
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            reviewImageRepository.save(ReviewImage.of(reviewId, imageUrls.get(i), i));
        }
        return imageUrls;
    }

    private ReviewResponse toResponse(Review review, List<String> imageUrls) {
        return new ReviewResponse(review.getId(), review.getAccommodationId(),
                mask(userService.findName(review.getUserId()).orElse("")), review.getRating(), review.getContent(),
                review.getSpotifyTrackId(), imageUrls, review.getHostReply(), review.getHostRepliedAt(),
                review.getCreatedAt());
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

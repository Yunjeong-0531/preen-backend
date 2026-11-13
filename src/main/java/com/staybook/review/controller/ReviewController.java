package com.staybook.review.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.response.PageResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.review.dto.ReviewDtos.CreateRequest;
import com.staybook.review.dto.ReviewDtos.ReviewResponse;
import com.staybook.review.dto.ReviewDtos.ReviewSort;
import com.staybook.review.dto.ReviewDtos.TrackResponse;
import com.staybook.review.dto.ReviewDtos.UpdateRequest;
import com.staybook.review.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 숙소 리뷰 목록 (별점 + 음악). 토큰 없이 접근한다.
     * sort: LATEST(기본, 최근 작성순), RATING_DESC(별점 높은순), RATING_ASC(별점 낮은순).
     * rating: 1~5를 주면 그 별점인 리뷰만 나온다. sort와 함께 쓸 수 있다.
     */
    @GetMapping("/api/accommodations/{id}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> getForAccommodation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "LATEST") ReviewSort sort,
            @RequestParam(required = false) @Min(value = 1, message = "평점은 1~5점입니다.")
            @Max(value = 5, message = "평점은 1~5점입니다.") Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.getForAccommodation(id, sort, rating, page, size));
    }

    /** 숙소별 트랙 목록(리뷰에 붙은 음악 모음). 토큰 없이 접근한다. */
    @GetMapping("/api/accommodations/{id}/tracks")
    public ApiResponse<List<TrackResponse>> getTracksForAccommodation(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.getTracksForAccommodation(id));
    }

    /** 리뷰 작성. 별점과 음악(Spotify 트랙 ID)은 필수다. */
    @PostMapping("/api/reviews")
    public ApiResponse<ReviewResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(reviewService.create(authUser.userId(), request));
    }

    /** 별점이나 음악을 바꾼다. 음악은 뗄 수 없고 다른 곡으로만 바꿀 수 있다. */
    @PatchMapping("/api/reviews/{id}")
    public ApiResponse<ReviewResponse> update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                              @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(reviewService.update(authUser.userId(), id, request));
    }

    @DeleteMapping("/api/reviews/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        reviewService.delete(authUser.userId(), id);
        return ApiResponse.ok();
    }
}

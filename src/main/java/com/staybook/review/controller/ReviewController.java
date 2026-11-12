package com.staybook.review.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.response.PageResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.review.dto.ReviewDtos.CreateRequest;
import com.staybook.review.dto.ReviewDtos.ReplyRequest;
import com.staybook.review.dto.ReviewDtos.ReviewResponse;
import com.staybook.review.dto.ReviewDtos.UpdateRequest;
import com.staybook.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 숙소 리뷰 목록. 토큰 없이 접근한다. */
    @GetMapping("/api/accommodations/{id}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> getForAccommodation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.getForAccommodation(id, page, size));
    }

    @PostMapping("/api/reviews")
    public ApiResponse<ReviewResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(reviewService.create(authUser.userId(), request));
    }

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

    /** 호스트 답글. HOST 전용이며 본인 숙소의 리뷰인지 서비스에서 검사한다. */
    @PostMapping("/api/host/reviews/{id}/reply")
    public ApiResponse<ReviewResponse> reply(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                             @Valid @RequestBody ReplyRequest request) {
        return ApiResponse.ok(reviewService.reply(authUser.userId(), id, request));
    }
}

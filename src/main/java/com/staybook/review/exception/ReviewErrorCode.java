package com.staybook.review.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-001", "리뷰를 찾을 수 없습니다."),
    NOT_MY_RESERVATION(HttpStatus.FORBIDDEN, "REVIEW-002", "본인의 예약에만 리뷰를 쓸 수 있습니다."),
    STAY_NOT_COMPLETED(HttpStatus.CONFLICT, "REVIEW-003", "투숙이 끝난 예약에만 리뷰를 쓸 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW-004", "이미 이 예약의 리뷰를 작성했습니다."),
    NOT_MY_REVIEW(HttpStatus.FORBIDDEN, "REVIEW-005", "본인이 쓴 리뷰만 수정하거나 삭제할 수 있습니다."),
    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-006", "숙소를 찾을 수 없습니다."),
    SPOTIFY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "REVIEW-007", "음악 검색이 설정되지 않았습니다."),
    SPOTIFY_ERROR(HttpStatus.BAD_GATEWAY, "REVIEW-008", "음악 검색 서비스와 통신하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

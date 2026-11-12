package com.staybook.favorite.exception;

import com.staybook.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FavoriteErrorCode implements ErrorCode {

    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE-001", "찜할 수 없는 숙소입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

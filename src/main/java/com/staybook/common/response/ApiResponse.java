package com.staybook.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 응답의 공통 규격.
 * 성공: success=true, code="OK". 실패: success=false, code=ErrorCode의 코드.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    private static final String OK_CODE = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, OK_CODE, null, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, OK_CODE, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}

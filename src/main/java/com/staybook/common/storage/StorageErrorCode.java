package com.staybook.common.storage;

import com.staybook.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum StorageErrorCode implements ErrorCode {

    EMPTY_FILE(HttpStatus.BAD_REQUEST, "STORAGE-001", "파일이 비어 있습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "STORAGE-002", "JPEG, PNG, GIF, WebP 이미지만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "STORAGE-003", "이미지 크기가 허용 범위를 넘었습니다."),
    INVALID_KEY(HttpStatus.BAD_REQUEST, "STORAGE-004", "저장 경로가 올바르지 않습니다."),
    STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE-005", "파일 저장에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    StorageErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

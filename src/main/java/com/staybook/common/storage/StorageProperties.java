package com.staybook.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param localRootDir 로컬 디스크 저장 폴더
 * @param baseUrl      반환 URL의 앞부분 (예: http://localhost:8080)
 * @param maxImageSize 이미지 한 장의 최대 크기
 */
@ConfigurationProperties(prefix = "staybook.storage")
public record StorageProperties(
        @DefaultValue("./uploads") String localRootDir,
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue("5MB") DataSize maxImageSize
) {

    /** 업로드 파일을 내려주는 URL 경로. */
    public static final String PUBLIC_PATH = "/uploads";
}

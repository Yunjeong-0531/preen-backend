package com.staybook.common.storage;

import java.io.InputStream;

/**
 * 파일 저장소. 1차 구현체는 로컬 디스크(LocalDiskStorageClient)이고, 이후 객체 저장소 구현체로 바꿀 수 있다.
 */
public interface StorageClient {

    /**
     * @param key         저장소 안의 상대 경로 (예: 2026/11/uuid.jpg)
     * @param content     파일 내용
     * @param contentType MIME 타입
     * @return 외부에서 접근할 수 있는 URL
     */
    String store(String key, InputStream content, String contentType);

    void delete(String key);
}

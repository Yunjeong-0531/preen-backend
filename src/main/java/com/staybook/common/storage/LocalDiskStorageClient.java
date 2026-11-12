package com.staybook.common.storage;

import com.staybook.common.exception.BaseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
public class LocalDiskStorageClient implements StorageClient {

    private final Path rootDir;
    private final String baseUrl;

    public LocalDiskStorageClient(StorageProperties properties) {
        this.rootDir = Path.of(properties.localRootDir()).toAbsolutePath().normalize();
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    @Override
    public String store(String key, InputStream content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BaseException(StorageErrorCode.STORE_FAILED, e);
        }
        return baseUrl + StorageProperties.PUBLIC_PATH + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new BaseException(StorageErrorCode.STORE_FAILED, e);
        }
    }

    public Path rootDir() {
        return rootDir;
    }

    /** 저장 폴더 밖을 가리키는 키(../ 등)는 거부한다. */
    private Path resolve(String key) {
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir) || target.equals(rootDir)) {
            throw new BaseException(StorageErrorCode.INVALID_KEY);
        }
        return target;
    }
}

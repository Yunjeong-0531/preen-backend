package com.staybook.common.storage;

import com.staybook.common.exception.BaseException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageUploadService {

    private final StorageClient storageClient;
    private final StorageProperties properties;
    private final Clock clock;

    public ImageUploadService(StorageClient storageClient, StorageProperties properties, Clock clock) {
        this.storageClient = storageClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** 형식과 크기를 검사한 뒤 저장하고 URL을 돌려준다. */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(StorageErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > properties.maxImageSize().toBytes()) {
            throw new BaseException(StorageErrorCode.IMAGE_TOO_LARGE);
        }
        ImageType type = detectType(file);

        LocalDate today = LocalDate.now(clock);
        // 파일명은 서버가 만든다. 원본 파일명은 경로에 쓰지 않는다.
        String key = "%d/%02d/%s.%s".formatted(
                today.getYear(), today.getMonthValue(), UUID.randomUUID(), type.extension());
        try (InputStream in = file.getInputStream()) {
            return storageClient.store(key, in, type.contentType());
        } catch (IOException e) {
            throw new BaseException(StorageErrorCode.STORE_FAILED, e);
        }
    }

    private ImageType detectType(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(ImageType.HEADER_LENGTH);
        } catch (IOException e) {
            throw new BaseException(StorageErrorCode.STORE_FAILED, e);
        }
        return ImageType.detect(header)
                .orElseThrow(() -> new BaseException(StorageErrorCode.UNSUPPORTED_IMAGE_TYPE));
    }
}

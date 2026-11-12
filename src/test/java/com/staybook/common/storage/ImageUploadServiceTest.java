package com.staybook.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.staybook.common.exception.BaseException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class ImageUploadServiceTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP_HEADER = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};

    @TempDir
    Path tempDir;

    private LocalDiskStorageClient storageClient;
    private ImageUploadService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties =
                new StorageProperties(tempDir.toString(), "http://localhost:8080/", DataSize.ofKilobytes(1));
        storageClient = new LocalDiskStorageClient(properties);
        service = new ImageUploadService(storageClient, properties,
                Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 이미지를_저장하고_URL을_돌려준다() throws Exception {
        String url = service.upload(new MockMultipartFile("file", "room.png", "image/png", PNG_HEADER));

        assertThat(url).matches("http://localhost:8080/uploads/2026/11/[0-9a-f-]{36}\\.png");
        Path saved = tempDir.resolve(url.substring("http://localhost:8080/uploads/".length()));
        assertThat(Files.readAllBytes(saved)).isEqualTo(PNG_HEADER);
    }

    @Test
    void 확장자는_파일_내용으로_정한다() {
        // 이름과 Content-Type은 png라고 주장하지만 내용은 JPEG, WebP
        assertThat(service.upload(new MockMultipartFile("file", "a.png", "image/png", JPEG_HEADER))).endsWith(".jpg");
        assertThat(service.upload(new MockMultipartFile("file", "a.png", "image/png", WEBP_HEADER))).endsWith(".webp");
    }

    @Test
    void 이미지가_아닌_파일은_Content_Type을_속여도_거부한다() {
        MockMultipartFile html = new MockMultipartFile("file", "evil.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> service.upload(html))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StorageErrorCode.UNSUPPORTED_IMAGE_TYPE));
    }

    @Test
    void 크기_제한을_넘으면_거부한다() {
        byte[] big = new byte[1025];
        System.arraycopy(PNG_HEADER, 0, big, 0, PNG_HEADER.length);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "big.png", "image/png", big)))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StorageErrorCode.IMAGE_TOO_LARGE));
    }

    @Test
    void 빈_파일은_거부한다() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StorageErrorCode.EMPTY_FILE));
    }

    @Test
    void 저장_폴더_밖을_가리키는_키는_거부한다() {
        assertThatThrownBy(() -> storageClient.store("../outside.png", new ByteArrayInputStream(PNG_HEADER), "image/png"))
                .isInstanceOfSatisfying(BaseException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(StorageErrorCode.INVALID_KEY));
        assertThat(tempDir.getParent().resolve("outside.png")).doesNotExist();
    }

    @Test
    void 삭제하면_파일이_없어지고_없는_파일을_지워도_에러가_없다() {
        storageClient.store("2026/11/a.png", new ByteArrayInputStream(PNG_HEADER), "image/png");
        assertThat(tempDir.resolve("2026/11/a.png")).exists();

        storageClient.delete("2026/11/a.png");
        storageClient.delete("2026/11/a.png");

        assertThat(tempDir.resolve("2026/11/a.png")).doesNotExist();
    }
}

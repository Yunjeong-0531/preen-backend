package com.staybook.common.storage;

import com.staybook.common.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageUploadService imageUploadService;

    public ImageController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    /** 로그인한 사용자만 호출할 수 있다. 숙소, 객실, 리뷰가 반환된 URL을 저장한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(new ImageUploadResponse(imageUploadService.upload(file)));
    }

    public record ImageUploadResponse(String url) {
    }
}

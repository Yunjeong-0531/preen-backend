package com.staybook.accommodation.controller;

import com.staybook.accommodation.dto.AccommodationDtos.AccommodationDetail;
import com.staybook.accommodation.dto.AccommodationDtos.AccommodationSummary;
import com.staybook.accommodation.dto.AccommodationDtos.AmenityResponse;
import com.staybook.accommodation.dto.AccommodationDtos.CategoryResponse;
import com.staybook.accommodation.dto.AccommodationDtos.RegionResponse;
import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.response.ApiResponse;
import com.staybook.common.response.PageResponse;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 조회. 토큰 없이 접근한다. */
@RestController
@RequiredArgsConstructor
@Validated
public class AccommodationController {

    private final AccommodationQueryService queryService;

    @GetMapping("/api/regions")
    public ApiResponse<List<RegionResponse>> getRegions() {
        return ApiResponse.ok(queryService.getRegions());
    }

    @GetMapping("/api/amenities")
    public ApiResponse<List<AmenityResponse>> getAmenities() {
        return ApiResponse.ok(queryService.getAmenities());
    }

    /** 숙소 카테고리(숙박 유형) 목록. 검색 필터와 숙소 등록 화면에서 쓴다. */
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.ok(queryService.getCategories());
    }

    /**
     * 목록 검색. 조건(regionId, categoryId, guestCount)은 모두 선택이고 함께 쓰면 모두 만족하는 숙소만 나온다.
     * page는 0부터, size는 최대 50.
     */
    @GetMapping("/api/accommodations")
    public ApiResponse<PageResponse<AccommodationSummary>> search(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Min(value = 1, message = "인원은 1명 이상이어야 합니다.") Integer guestCount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queryService.search(regionId, categoryId, guestCount, page, size));
    }

    @GetMapping("/api/accommodations/{id}")
    public ApiResponse<AccommodationDetail> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getListedDetail(id));
    }
}

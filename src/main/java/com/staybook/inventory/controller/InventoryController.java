package com.staybook.inventory.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.inventory.dto.InventoryDtos.InventoryResponse;
import com.staybook.inventory.dto.InventoryDtos.RoomAvailability;
import com.staybook.inventory.dto.InventoryDtos.SetInventoriesRequest;
import com.staybook.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** 객실별 예약 가능 여부와 기간 합계 요금. 토큰 없이 접근한다. 날짜는 yyyy-MM-dd. */
    @GetMapping("/api/accommodations/{id}/availability")
    public ApiResponse<List<RoomAvailability>> getAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return ApiResponse.ok(inventoryService.getAvailability(id, checkIn, checkOut));
    }

    /**
     * 기간별 재고 조회 (양 끝 포함, 최대 366일). 날짜별 수량, 예약·선점 수, 가격, 판매 마감 여부.
     * HOST 전용이며 본인 객실인지 서비스에서 검사한다. 날짜는 yyyy-MM-dd.
     */
    @GetMapping("/api/host/rooms/{roomId}/inventories")
    public ApiResponse<List<InventoryResponse>> getInventories(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(inventoryService.getInventories(authUser.userId(), roomId, startDate, endDate));
    }

    /** 기간별 수량, 가격, 판매 마감 설정. HOST 전용이며 본인 객실인지 서비스에서 검사한다. */
    @PutMapping("/api/host/rooms/{roomId}/inventories")
    public ApiResponse<List<InventoryResponse>> setInventories(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long roomId,
                                                               @Valid @RequestBody SetInventoriesRequest request) {
        return ApiResponse.ok(inventoryService.setInventories(authUser.userId(), roomId, request));
    }
}

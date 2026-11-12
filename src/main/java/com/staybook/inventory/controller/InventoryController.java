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

    /** 기간별 수량, 가격, 판매 마감 설정. HOST 전용이며 본인 객실인지 서비스에서 검사한다. */
    @PutMapping("/api/host/rooms/{roomId}/inventories")
    public ApiResponse<List<InventoryResponse>> setInventories(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long roomId,
                                                               @Valid @RequestBody SetInventoriesRequest request) {
        return ApiResponse.ok(inventoryService.setInventories(authUser.userId(), roomId, request));
    }
}

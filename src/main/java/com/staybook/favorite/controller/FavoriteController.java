package com.staybook.favorite.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.response.PageResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.favorite.dto.FavoriteResponse;
import com.staybook.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{accommodationId}")
    public ApiResponse<Void> add(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long accommodationId) {
        favoriteService.add(authUser.userId(), accommodationId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{accommodationId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long accommodationId) {
        favoriteService.remove(authUser.userId(), accommodationId);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<FavoriteResponse>> getMine(@AuthenticationPrincipal AuthUser authUser,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(favoriteService.getMine(authUser.userId(), page, size));
    }
}

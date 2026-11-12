package com.staybook.user.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.user.dto.UpdateMeRequest;
import com.staybook.user.dto.UserMeResponse;
import com.staybook.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(userService.getMe(authUser.userId()));
    }

    @PatchMapping
    public ApiResponse<UserMeResponse> updateMe(@AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody UpdateMeRequest request) {
        return ApiResponse.ok(userService.updateMe(authUser.userId(), request));
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthUser authUser) {
        userService.withdraw(authUser.userId());
        return ApiResponse.ok();
    }
}

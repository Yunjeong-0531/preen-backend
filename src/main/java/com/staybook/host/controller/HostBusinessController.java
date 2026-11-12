package com.staybook.host.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.common.security.AuthUser;
import com.staybook.host.dto.HostDtos;
import com.staybook.host.service.HostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HostBusinessController {

    private final HostService hostService;

    /** 로그인한 회원이면 누구나 호출한다 (/api/host/** 가 아니므로 GUEST도 접근 가능). */
    @PostMapping("/api/host-businesses")
    public ApiResponse<HostDtos.RegisterResponse> register(@AuthenticationPrincipal AuthUser authUser,
                                                           @Valid @RequestBody HostDtos.RegisterRequest request) {
        return ApiResponse.ok(hostService.register(authUser.userId(), request));
    }

    @GetMapping("/api/host/business")
    public ApiResponse<HostDtos.BusinessResponse> getMyBusiness(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(hostService.getMyBusiness(authUser.userId()));
    }
}

package com.staybook.terms.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.terms.dto.TermResponse;
import com.staybook.terms.service.TermsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    /** 현재 유효한 약관 목록. 토큰 없이 접근한다. */
    @GetMapping
    public ApiResponse<List<TermResponse>> getCurrentTerms() {
        return ApiResponse.ok(termsService.getCurrentTerms());
    }
}

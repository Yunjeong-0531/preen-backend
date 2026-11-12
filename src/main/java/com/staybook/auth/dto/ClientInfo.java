package com.staybook.auth.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** 요청을 보낸 기기 정보. 로그인 시도 기록과 리프레시 토큰 값에 함께 남긴다. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}

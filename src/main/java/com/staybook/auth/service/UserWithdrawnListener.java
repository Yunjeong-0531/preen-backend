package com.staybook.auth.service;

import com.staybook.user.service.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 회원의 리프레시 토큰을 전부 지운다. */
@Component
@RequiredArgsConstructor
public class UserWithdrawnListener {

    private final RefreshTokenStore refreshTokenStore;

    @EventListener
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        refreshTokenStore.deleteAll(event.userId());
    }
}

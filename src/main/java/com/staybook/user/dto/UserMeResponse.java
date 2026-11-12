package com.staybook.user.dto;

import com.staybook.common.security.UserRole;
import java.time.LocalDate;

public record UserMeResponse(
        Long id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        UserRole role,
        boolean signupCompleted
) {
}

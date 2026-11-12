package com.staybook.user.dto;

import java.time.LocalDate;
import java.util.List;

/** 이메일 가입 시 auth가 넘기는 값. phone은 없을 수 있다. */
public record RegisterCommand(
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        List<Long> agreedTermIds
) {
}

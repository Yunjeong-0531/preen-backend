package com.staybook.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보내지 않은(null) 항목은 바꾸지 않는다. */
public record UpdateMeRequest(
        @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "이름은 공백만으로 이루어질 수 없습니다.")
        String name,

        @Pattern(regexp = "^[0-9\\-+ ]{9,20}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone
) {
}

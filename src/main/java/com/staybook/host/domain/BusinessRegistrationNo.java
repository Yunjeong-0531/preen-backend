package com.staybook.host.domain;

import java.util.Optional;

/**
 * 사업자등록번호 형식 검증. 숫자 10자리(하이픈 허용)이고 마지막 자리가 검증 번호와 맞아야 한다.
 * 국세청에 실제 등록된 번호인지는 확인하지 않는다.
 */
public final class BusinessRegistrationNo {

    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private BusinessRegistrationNo() {
    }

    /** 형식이 맞으면 하이픈을 뺀 숫자 10자리를 돌려준다. */
    public static Optional<String> normalize(String input) {
        if (input == null || !input.matches("^\\d{3}-?\\d{2}-?\\d{5}$")) {
            return Optional.empty();
        }
        String digits = input.replace("-", "");
        return checkDigit(digits.substring(0, 9)) == digits.charAt(9) - '0'
                ? Optional.of(digits)
                : Optional.empty();
    }

    /** 앞 9자리에 대한 검증 번호. */
    public static int checkDigit(String firstNine) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (firstNine.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += (firstNine.charAt(8) - '0') * 5 / 10;
        return (10 - sum % 10) % 10;
    }
}

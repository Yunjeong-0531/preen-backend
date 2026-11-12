package com.staybook.host.dto;

import com.staybook.host.domain.HostBusiness;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** host 요청·응답 모음. */
public final class HostDtos {

    private HostDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "상호를 입력해 주세요.") @Size(max = 100) String businessName,
            @NotBlank(message = "사업자등록번호를 입력해 주세요.") @Size(max = 20) String businessRegistrationNo
    ) {
    }

    public record BusinessResponse(
            Long id,
            String businessName,
            String businessRegistrationNo,
            String approvalStatus,
            LocalDateTime createdAt
    ) {

        public static BusinessResponse from(HostBusiness business) {
            return new BusinessResponse(business.getId(), business.getBusinessName(),
                    business.getBusinessRegistrationNo(), business.getApprovalStatus().name(),
                    business.getCreatedAt());
        }
    }

    /**
     * 사업자 등록 결과. 역할이 HOST로 바뀌었으므로 새 액세스 토큰을 함께 준다.
     *
     * @param accessTokenExpiresIn 액세스 토큰 유효 시간(초)
     */
    public record RegisterResponse(BusinessResponse business, String accessToken, long accessTokenExpiresIn) {
    }

    /** 다른 feature가 소유 여부 검사에 쓰는 요약. */
    public record BusinessInfo(Long businessId, Long hostUserId) {
    }
}

package com.staybook.host.service;

import com.staybook.common.exception.BaseException;
import com.staybook.common.security.JwtProvider;
import com.staybook.common.security.UserRole;
import com.staybook.host.domain.BusinessRegistrationNo;
import com.staybook.host.domain.HostBusiness;
import com.staybook.host.dto.HostDtos;
import com.staybook.host.exception.HostErrorCode;
import com.staybook.host.repository.HostBusinessRepository;
import com.staybook.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostService {

    private final HostBusinessRepository businessRepository;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    /**
     * 사업자 등록. 번호 형식 검증을 통과하면 바로 APPROVED로 저장하고 회원 역할을 HOST로 바꾼다.
     * 기존 액세스 토큰에는 예전 역할이 들어 있으므로 새 토큰을 응답에 담는다.
     */
    @Transactional
    public HostDtos.RegisterResponse register(Long userId, HostDtos.RegisterRequest request) {
        String registrationNo = BusinessRegistrationNo.normalize(request.businessRegistrationNo())
                .orElseThrow(() -> new BaseException(HostErrorCode.INVALID_REGISTRATION_NO));
        if (businessRepository.existsByHostUserId(userId)) {
            throw new BaseException(HostErrorCode.BUSINESS_ALREADY_REGISTERED);
        }
        if (businessRepository.existsByBusinessRegistrationNo(registrationNo)) {
            throw new BaseException(HostErrorCode.DUPLICATE_REGISTRATION_NO);
        }

        HostBusiness business;
        try {
            business = businessRepository.saveAndFlush(HostBusiness.approved(
                    userId, request.businessName().trim(), registrationNo, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 같은 번호로 동시에 등록한 경우 유니크 제약에 걸린다
            throw new BaseException(HostErrorCode.DUPLICATE_REGISTRATION_NO);
        }
        userService.changeRole(userId, UserRole.HOST);

        return new HostDtos.RegisterResponse(
                HostDtos.BusinessResponse.from(business),
                jwtProvider.createAccessToken(userId, UserRole.HOST),
                jwtProvider.accessTokenTtl().toSeconds());
    }

    @Transactional(readOnly = true)
    public HostDtos.BusinessResponse getMyBusiness(Long userId) {
        return businessRepository.findFirstByHostUserIdOrderByIdAsc(userId)
                .map(HostDtos.BusinessResponse::from)
                .orElseThrow(() -> new BaseException(HostErrorCode.BUSINESS_NOT_FOUND));
    }

    // ---------- 다른 feature용 ----------

    /** 그 회원의 사업자. 없으면 예외. */
    @Transactional(readOnly = true)
    public HostDtos.BusinessInfo getBusinessOf(Long userId) {
        return businessRepository.findFirstByHostUserIdOrderByIdAsc(userId)
                .map(b -> new HostDtos.BusinessInfo(b.getId(), b.getHostUserId()))
                .orElseThrow(() -> new BaseException(HostErrorCode.BUSINESS_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findHostUserId(Long businessId) {
        return businessRepository.findById(businessId).map(HostBusiness::getHostUserId);
    }
}

package com.staybook.auth.repository;

import com.staybook.auth.domain.VerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /** 가장 최근에 발급한 코드. 재발송하면 이전 코드는 더 이상 쓰이지 않는다. */
    Optional<VerificationCode> findFirstByTargetHashAndPurposeOrderByIdDesc(String targetHash, String purpose);
}

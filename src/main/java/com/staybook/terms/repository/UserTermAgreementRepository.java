package com.staybook.terms.repository;

import com.staybook.terms.domain.UserTermAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermAgreementRepository extends JpaRepository<UserTermAgreement, Long> {

    List<UserTermAgreement> findByUserId(Long userId);
}

package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.RefundPolicyRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRuleRepository extends JpaRepository<RefundPolicyRule, Long> {

    List<RefundPolicyRule> findByRefundPolicyIdOrderByDaysBeforeDesc(Long refundPolicyId);
}

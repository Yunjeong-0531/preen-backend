package com.staybook.payment.repository;

import com.staybook.payment.domain.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

    boolean existsByProviderEventId(String providerEventId);
}

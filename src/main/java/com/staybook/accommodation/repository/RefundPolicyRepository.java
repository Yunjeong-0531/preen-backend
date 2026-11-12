package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.RefundPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    List<RefundPolicy> findByAccommodationIdAndActiveTrue(Long accommodationId);

    Optional<RefundPolicy> findFirstByAccommodationIdAndActiveTrueOrderByIdDesc(Long accommodationId);

    long countByAccommodationId(Long accommodationId);
}

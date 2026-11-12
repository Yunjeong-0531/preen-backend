package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.RefundPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    List<RefundPolicy> findByAccommodationIdAndActiveTrue(Long accommodationId);

    List<RefundPolicy> findByAccommodationIdInAndActiveTrue(Collection<Long> accommodationIds);

    Optional<RefundPolicy> findFirstByAccommodationIdAndActiveTrueOrderByIdDesc(Long accommodationId);

    long countByAccommodationId(Long accommodationId);
}

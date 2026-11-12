package com.staybook.host.repository;

import com.staybook.host.domain.HostBusiness;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostBusinessRepository extends JpaRepository<HostBusiness, Long> {

    Optional<HostBusiness> findFirstByHostUserIdOrderByIdAsc(Long hostUserId);

    boolean existsByHostUserId(Long hostUserId);

    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);
}

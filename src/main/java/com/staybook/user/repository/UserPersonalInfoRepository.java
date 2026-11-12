package com.staybook.user.repository;

import com.staybook.user.domain.UserPersonalInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPersonalInfoRepository extends JpaRepository<UserPersonalInfo, Long> {

    boolean existsByEmailHash(String emailHash);

    Optional<UserPersonalInfo> findByEmailHash(String emailHash);
}

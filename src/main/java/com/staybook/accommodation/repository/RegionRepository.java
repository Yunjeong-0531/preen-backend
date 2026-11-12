package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findByParentId(Long parentId);
}

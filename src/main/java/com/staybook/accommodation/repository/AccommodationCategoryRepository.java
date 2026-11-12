package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.AccommodationCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccommodationCategoryRepository extends JpaRepository<AccommodationCategory, Long> {

    List<AccommodationCategory> findAllByOrderBySortOrderAscIdAsc();
}

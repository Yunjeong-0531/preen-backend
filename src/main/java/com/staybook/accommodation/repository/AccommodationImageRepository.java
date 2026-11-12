package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.AccommodationImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationImageRepository extends JpaRepository<AccommodationImage, Long> {

    List<AccommodationImage> findByAccommodationIdOrderBySortOrderAsc(Long accommodationId);

    List<AccommodationImage> findByAccommodationIdInOrderBySortOrderAsc(Collection<Long> accommodationIds);

    @Modifying
    @Query("delete from AccommodationImage i where i.accommodationId = :accommodationId")
    void deleteAllByAccommodationId(@Param("accommodationId") Long accommodationId);
}

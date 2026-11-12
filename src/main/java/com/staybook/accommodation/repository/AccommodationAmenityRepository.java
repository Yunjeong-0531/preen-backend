package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.AccommodationAmenity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationAmenityRepository
        extends JpaRepository<AccommodationAmenity, AccommodationAmenity.Key> {

    @Query("select l from AccommodationAmenity l where l.id.accommodationId = :accommodationId")
    List<AccommodationAmenity> findByAccommodationId(@Param("accommodationId") Long accommodationId);

    @Modifying
    @Query("delete from AccommodationAmenity l where l.id.accommodationId = :accommodationId")
    void deleteAllByAccommodationId(@Param("accommodationId") Long accommodationId);
}

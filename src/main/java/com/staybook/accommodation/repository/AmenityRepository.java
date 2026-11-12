package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {
}

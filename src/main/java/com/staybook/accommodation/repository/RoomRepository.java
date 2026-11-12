package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.Room;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByAccommodationIdOrderByIdAsc(Long accommodationId);

    List<Room> findByAccommodationIdIn(Collection<Long> accommodationIds);

    List<Room> findByStatus(Room.RoomStatus status);

    boolean existsByAccommodationIdAndStatus(Long accommodationId, Room.RoomStatus status);

    long countByAccommodationIdAndStatus(Long accommodationId, Room.RoomStatus status);
}

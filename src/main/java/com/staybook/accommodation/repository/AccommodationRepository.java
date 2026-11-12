package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.Accommodation;
import com.staybook.accommodation.domain.Room;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    java.util.List<Accommodation> findByHostBusinessId(Long hostBusinessId);

    /**
     * 목록 검색. 지역 조건을 쓰지 않을 때는 filterRegion=false, 인원 조건을 쓰지 않을 때는 guestCount=null로 넘긴다.
     * 인원 조건은 roomStatus인 객실 중 최대 인원이 guestCount 이상인 객실이 있는지로 본다.
     */
    @Query("""
            select a from Accommodation a
            where a.listingStatus = :listingStatus
              and (:filterRegion = false or a.regionId in :regionIds)
              and (:guestCount is null or exists (
                    select 1 from Room r
                    where r.accommodationId = a.id
                      and r.status = :roomStatus
                      and r.maxCapacity >= :guestCount))
            """)
    Page<Accommodation> search(@Param("listingStatus") Accommodation.ListingStatus listingStatus,
                               @Param("roomStatus") Room.RoomStatus roomStatus,
                               @Param("filterRegion") boolean filterRegion,
                               @Param("regionIds") Collection<Long> regionIds,
                               @Param("guestCount") Integer guestCount,
                               Pageable pageable);
}

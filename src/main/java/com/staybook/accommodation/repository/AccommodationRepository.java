package com.staybook.accommodation.repository;

import com.staybook.accommodation.domain.Accommodation;
import com.staybook.accommodation.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

    java.util.List<Accommodation> findByHostBusinessId(Long hostBusinessId);

    java.util.List<Accommodation> findByHostBusinessIdOrderByIdDesc(Long hostBusinessId);

    /** 상태를 바꾸기 전에 숙소 행을 잠근다. 공개 전환과 객실 판매 중지가 동시에 와도 한쪽씩 처리한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Accommodation a where a.id = :id")
    Optional<Accommodation> findByIdForUpdate(@Param("id") Long id);

    /**
     * 목록 검색. 지역 조건을 쓰지 않을 때는 filterRegion=false,
     * 카테고리·인원 조건을 쓰지 않을 때는 categoryId=null, guestCount=null로 넘긴다.
     * 인원 조건은 roomStatus인 객실 중 최대 인원이 guestCount 이상인 객실이 있는지로 본다.
     */
    @Query("""
            select a from Accommodation a
            where a.listingStatus = :listingStatus
              and (:filterRegion = false or a.regionId in :regionIds)
              and (:categoryId is null or a.categoryId = :categoryId)
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
                               @Param("categoryId") Long categoryId,
                               @Param("guestCount") Integer guestCount,
                               Pageable pageable);
}

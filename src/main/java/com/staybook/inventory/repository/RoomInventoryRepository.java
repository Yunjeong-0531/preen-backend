package com.staybook.inventory.repository;

import com.staybook.inventory.domain.RoomInventory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {

    // ---------- 조건부 UPDATE (영향받은 행 수를 돌려준다) ----------

    /** 선점. 0이면 매진(또는 판매 중지)이다. */
    @Modifying
    @Query("""
            update RoomInventory i set i.heldCount = i.heldCount + 1
            where i.id = :id and i.closed = false and i.bookedCount + i.heldCount < i.totalCount
            """)
    int tryHold(@Param("id") Long id);

    /** 결제 완료: 선점 1개를 확정으로 옮긴다. */
    @Modifying
    @Query("""
            update RoomInventory i set i.heldCount = i.heldCount - 1, i.bookedCount = i.bookedCount + 1
            where i.id = :id and i.heldCount > 0
            """)
    int moveHeldToBooked(@Param("id") Long id);

    @Modifying
    @Query("update RoomInventory i set i.heldCount = i.heldCount - 1 where i.id = :id and i.heldCount > 0")
    int decreaseHeld(@Param("id") Long id);

    @Modifying
    @Query("update RoomInventory i set i.bookedCount = i.bookedCount - 1 where i.id = :id and i.bookedCount > 0")
    int decreaseBooked(@Param("id") Long id);

    /** 이미 있는 날짜는 건드리지 않고 없는 날짜만 만든다 (MySQL INSERT IGNORE). */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO room_inventories
                (room_id, stay_date, total_count, booked_count, held_count, price, is_closed)
            VALUES (:roomId, :stayDate, :totalCount, 0, 0, :price, FALSE)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("roomId") Long roomId, @Param("stayDate") LocalDate stayDate,
                       @Param("totalCount") int totalCount, @Param("price") BigDecimal price);

    // ---------- 조회 ----------

    /** 선점용. 엔티티를 영속성 컨텍스트에 올리지 않도록 필요한 값만 읽는다. 날짜 오름차순. */
    @Query("""
            select i.id as id, i.stayDate as stayDate, i.price as price from RoomInventory i
            where i.roomId = :roomId and i.stayDate >= :from and i.stayDate < :to
            order by i.stayDate asc
            """)
    List<NightView> findNights(@Param("roomId") Long roomId, @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** from 이상 to 미만 (숙박일 기준). */
    @Query("""
            select i from RoomInventory i
            where i.roomId in :roomIds and i.stayDate >= :from and i.stayDate < :to
            order by i.stayDate asc
            """)
    List<RoomInventory> findForStay(@Param("roomIds") Collection<Long> roomIds, @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    List<RoomInventory> findByRoomIdAndStayDateBetweenOrderByStayDateAsc(Long roomId, LocalDate start,
                                                                         LocalDate endInclusive);

    @Query("select i.stayDate from RoomInventory i where i.roomId = :roomId and i.stayDate between :start and :end")
    List<LocalDate> findDates(@Param("roomId") Long roomId, @Param("start") LocalDate start,
                              @Param("end") LocalDate endInclusive);

    Optional<RoomInventory> findFirstByRoomIdOrderByStayDateDesc(Long roomId);

    interface NightView {

        Long getId();

        LocalDate getStayDate();

        BigDecimal getPrice();
    }
}

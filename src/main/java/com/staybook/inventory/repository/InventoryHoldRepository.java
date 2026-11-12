package com.staybook.inventory.repository;

import com.staybook.inventory.domain.InventoryHold;
import com.staybook.inventory.domain.InventoryHold.HoldStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryHoldRepository extends JpaRepository<InventoryHold, Long> {

    /** 예약의 선점 행 (id와 재고 id만). 선점과 같은 순서(날짜 오름차순)로 잠그도록 정렬한다. */
    @Query("""
            select h.id as id, h.roomInventoryId as roomInventoryId
            from InventoryHold h, RoomInventory i
            where i.id = h.roomInventoryId and h.reservationId = :reservationId
            order by i.stayDate asc
            """)
    List<HoldView> findByReservation(@Param("reservationId") Long reservationId);

    /** 상태를 from에서 to로 바꾼다. 이미 다른 상태면 0을 돌려주므로 같은 요청이 두 번 와도 한 번만 반영된다. */
    @Modifying
    @Query("update InventoryHold h set h.status = :to where h.id = :id and h.status = :from")
    int transition(@Param("id") Long id, @Param("from") HoldStatus from, @Param("to") HoldStatus to);

    interface HoldView {

        Long getId();

        Long getRoomInventoryId();
    }
}

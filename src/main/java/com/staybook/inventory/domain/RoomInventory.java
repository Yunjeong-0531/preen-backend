package com.staybook.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 객실의 날짜별 재고·가격. (room_id, stay_date) 유니크.
 * booked_count, held_count는 조건부 UPDATE 쿼리로만 바꾼다. 엔티티로 수정할 때 이 값들을 덮어쓰지 않도록
 * 바뀐 컬럼만 UPDATE한다(@DynamicUpdate).
 */
@Entity
@Table(name = "room_inventories")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate stayDate;

    @Column(nullable = false, columnDefinition = "smallint")
    private int totalCount;

    /** 확정된 수량 */
    @Column(nullable = false, columnDefinition = "smallint", updatable = false)
    private int bookedCount;

    /** 결제 대기로 선점 중인 수량 */
    @Column(nullable = false, columnDefinition = "smallint", updatable = false)
    private int heldCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    public static RoomInventory create(Long roomId, LocalDate stayDate, int totalCount, BigDecimal price,
                                       boolean closed) {
        RoomInventory inventory = new RoomInventory();
        inventory.roomId = roomId;
        inventory.stayDate = stayDate;
        inventory.totalCount = totalCount;
        inventory.price = price;
        inventory.closed = closed;
        return inventory;
    }

    /** null인 항목은 바꾸지 않는다. */
    public void change(Integer totalCount, BigDecimal price, Boolean closed) {
        if (totalCount != null) {
            this.totalCount = totalCount;
        }
        if (price != null) {
            this.price = price;
        }
        if (closed != null) {
            this.closed = closed;
        }
    }

    public int remaining() {
        return totalCount - bookedCount - heldCount;
    }

    public boolean isAvailable() {
        return !closed && remaining() > 0;
    }
}

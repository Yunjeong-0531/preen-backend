package com.staybook.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결제 대기 중 재고 선점. 숙박일마다 한 행(2박이면 2행).
 * 상태 전이(HELD → CONFIRMED / RELEASED, CONFIRMED → RELEASED)는 조건부 UPDATE 쿼리로만 한다.
 */
@Entity
@Table(name = "inventory_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryHold {

    public enum HoldStatus {
        HELD,
        CONFIRMED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long roomInventoryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public static InventoryHold held(Long reservationId, Long roomInventoryId, LocalDateTime expiresAt) {
        InventoryHold hold = new InventoryHold();
        hold.reservationId = reservationId;
        hold.roomInventoryId = roomInventoryId;
        hold.status = HoldStatus.HELD;
        hold.expiresAt = expiresAt;
        return hold;
    }
}

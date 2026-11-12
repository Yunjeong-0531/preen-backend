package com.staybook.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 예약 금액의 항목별 내역. total_price는 이 항목들의 합계다. */
@Entity
@Table(name = "reservation_price_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPriceItem {

    public enum ItemType {
        /** 숙박일별 판매가의 합 */
        ROOM,
        /** (인원 - 기준 인원) × 추가 인원 요금 × 박수 */
        EXTRA_PERSON
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public static ReservationPriceItem of(Long reservationId, ItemType itemType, String name, BigDecimal amount) {
        ReservationPriceItem item = new ReservationPriceItem();
        item.reservationId = reservationId;
        item.itemType = itemType;
        item.name = name;
        item.amount = amount;
        return item;
    }
}

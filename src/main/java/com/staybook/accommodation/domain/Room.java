package com.staybook.accommodation.domain;

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

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    /** ACTIVE: 판매 중, INACTIVE: 판매 중지 */
    public enum RoomStatus {
        ACTIVE,
        INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accommodationId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 기준 인원 */
    @Column(nullable = false)
    private int capacity;

    /** 최대 인원 */
    @Column(nullable = false)
    private int maxCapacity;

    /** 기준 인원을 넘는 1인 1박당 추가 요금 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal extraPersonFee;

    /** 기본가. 재고 자동 생성 시 날짜별 가격의 초깃값이 된다 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    public static Room create(Long accommodationId, String name, int capacity, int maxCapacity,
                              BigDecimal extraPersonFee, BigDecimal nightlyPrice) {
        Room room = new Room();
        room.accommodationId = accommodationId;
        room.name = name;
        room.capacity = capacity;
        room.maxCapacity = maxCapacity;
        room.extraPersonFee = extraPersonFee;
        room.nightlyPrice = nightlyPrice;
        room.status = RoomStatus.ACTIVE;
        return room;
    }

    /** null인 항목은 바꾸지 않는다. */
    public void update(String name, Integer capacity, Integer maxCapacity, BigDecimal extraPersonFee,
                       BigDecimal nightlyPrice, RoomStatus status) {
        if (name != null) {
            this.name = name;
        }
        if (capacity != null) {
            this.capacity = capacity;
        }
        if (maxCapacity != null) {
            this.maxCapacity = maxCapacity;
        }
        if (extraPersonFee != null) {
            this.extraPersonFee = extraPersonFee;
        }
        if (nightlyPrice != null) {
            this.nightlyPrice = nightlyPrice;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public boolean isActive() {
        return status == RoomStatus.ACTIVE;
    }
}

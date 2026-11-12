package com.staybook.reservation.domain;

import com.staybook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 예약 본체. 예약 1건 = 객실 1개.
 * 예약 시점의 예약자 이름·연락처와 환불정책(JSON 스냅샷)을 함께 저장해, 이후 회원 정보나 정책이 바뀌어도 보존한다.
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    public enum Status {
        PENDING,
        CONFIRMED,
        CANCELLED,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String reservationNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long refundPolicyId;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false, columnDefinition = "smallint")
    private int guestCount;

    @Column(nullable = false, length = 100)
    private String guestName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String guestPhone;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime holdExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String refundPolicySnapshot;

    private LocalDateTime cancelledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Reservation pending(String reservationNo, Long userId, Long roomId, Long refundPolicyId,
                                      LocalDate checkIn, LocalDate checkOut, int guestCount, String guestName,
                                      String guestPhone, String refundPolicySnapshot, LocalDateTime now,
                                      LocalDateTime holdExpiresAt) {
        Reservation r = new Reservation();
        r.reservationNo = reservationNo;
        r.userId = userId;
        r.roomId = roomId;
        r.refundPolicyId = refundPolicyId;
        r.checkIn = checkIn;
        r.checkOut = checkOut;
        r.guestCount = guestCount;
        r.guestName = guestName;
        r.guestPhone = guestPhone;
        r.totalPrice = BigDecimal.ZERO;
        r.status = Status.PENDING;
        r.holdExpiresAt = holdExpiresAt;
        r.refundPolicySnapshot = refundPolicySnapshot;
        r.createdAt = now;
        return r;
    }

    public void changeTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void confirm() {
        this.status = Status.CONFIRMED;
    }

    public void cancel(LocalDateTime now) {
        this.status = Status.CANCELLED;
        this.cancelledAt = now;
    }

    public void complete() {
        this.status = Status.COMPLETED;
    }

    public boolean isStatus(Status status) {
        return this.status == status;
    }
}

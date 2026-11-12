package com.staybook.reservation.domain;

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

/** 예약 상태 변경 이력. 시스템(스케줄러, 결제)이 바꾼 경우 changedBy는 null이다. */
@Entity
@Table(name = "reservation_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    private Long changedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Reservation.Status previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Reservation.Status newStatus;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ReservationStatusHistory of(Long reservationId, Long changedBy, Reservation.Status previous,
                                              Reservation.Status next, String reason, LocalDateTime now) {
        ReservationStatusHistory history = new ReservationStatusHistory();
        history.reservationId = reservationId;
        history.changedBy = changedBy;
        history.previousStatus = previous;
        history.newStatus = next;
        history.reason = reason;
        history.createdAt = now;
        return history;
    }
}

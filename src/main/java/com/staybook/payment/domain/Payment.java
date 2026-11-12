package com.staybook.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 결제 건. order_id는 중복 결제 방지용 멱등키, payment_key는 PG사 결제 식별자다. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    public enum Status {
        READY,
        PAID,
        FAILED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 30)
    private String pgProvider;

    @Column(length = 200)
    private String paymentKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 30)
    private String method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    private LocalDateTime paidAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Payment ready(Long reservationId, String orderId, String pgProvider, BigDecimal amount,
                                LocalDateTime now) {
        Payment payment = new Payment();
        payment.reservationId = reservationId;
        payment.orderId = orderId;
        payment.pgProvider = pgProvider;
        payment.amount = amount;
        payment.status = Status.READY;
        payment.createdAt = now;
        return payment;
    }

    public void markPaid(String paymentKey, String method, LocalDateTime now) {
        this.paymentKey = paymentKey;
        this.method = method;
        this.status = Status.PAID;
        this.paidAt = now;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public void markRefunded() {
        this.status = Status.REFUNDED;
    }

    public boolean isStatus(Status status) {
        return this.status == status;
    }
}

package com.staybook.payment.repository;

import com.staybook.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 같은 order_id의 요청이 동시에 와도 한 번만 처리되도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.reservationId = :reservationId and p.status = :status order by p.id")
    List<Payment> findByReservationIdAndStatusForUpdate(@Param("reservationId") Long reservationId,
                                                        @Param("status") Payment.Status status);

    Optional<Payment> findFirstByReservationIdAndStatusOrderByIdDesc(Long reservationId, Payment.Status status);
}

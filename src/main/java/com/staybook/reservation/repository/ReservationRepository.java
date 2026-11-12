package com.staybook.reservation.repository;

import com.staybook.reservation.domain.Reservation;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 상태를 바꾸기 전에 행을 잠그고 읽는다 (SELECT ... FOR UPDATE).
     * 결제 확정, 사용자 취소, 만료 스케줄러가 같은 예약을 동시에 바꾸지 못하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByRoomIdIn(Collection<Long> roomIds, Pageable pageable);

    @Query("select r.id from Reservation r where r.status = :status and r.holdExpiresAt <= :now order by r.id")
    List<Long> findIdsByStatusAndHoldExpired(@Param("status") Reservation.Status status,
                                             @Param("now") LocalDateTime now);

    @Query("select r.id from Reservation r where r.status = :status and r.checkOut < :today order by r.id")
    List<Long> findIdsByStatusAndCheckOutBefore(@Param("status") Reservation.Status status,
                                                @Param("today") LocalDate today);
}

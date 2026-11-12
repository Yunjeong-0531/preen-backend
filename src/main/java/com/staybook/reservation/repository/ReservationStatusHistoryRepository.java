package com.staybook.reservation.repository;

import com.staybook.reservation.domain.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {
}

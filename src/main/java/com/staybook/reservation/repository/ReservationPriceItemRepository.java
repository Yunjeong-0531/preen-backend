package com.staybook.reservation.repository;

import com.staybook.reservation.domain.ReservationPriceItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPriceItemRepository extends JpaRepository<ReservationPriceItem, Long> {

    List<ReservationPriceItem> findByReservationIdInOrderByIdAsc(Collection<Long> reservationIds);
}

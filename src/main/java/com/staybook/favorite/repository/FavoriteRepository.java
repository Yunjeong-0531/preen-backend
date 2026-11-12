package com.staybook.favorite.repository;

import com.staybook.favorite.domain.Favorite;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** 이미 찜한 숙소면 아무 일도 하지 않는다 (MySQL INSERT IGNORE). 동시에 두 번 눌러도 한 행만 남는다. */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO favorites (user_id, accommodation_id, created_at)
            VALUES (:userId, :accommodationId, :now)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("accommodationId") Long accommodationId,
                       @Param("now") LocalDateTime now);

    /** 없는 찜이면 0행이 지워질 뿐 에러가 아니다. */
    @Modifying
    @Query("delete from Favorite f where f.userId = :userId and f.accommodationId = :accommodationId")
    int deleteByUserAndAccommodation(@Param("userId") Long userId, @Param("accommodationId") Long accommodationId);

    Page<Favorite> findByUserId(Long userId, Pageable pageable);
}

package com.staybook.terms.repository;

import com.staybook.terms.domain.Term;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

    /** 시행일이 지난 약관 전체 (종류별 과거 버전 포함). */
    List<Term> findByEffectiveAtLessThanEqual(LocalDateTime now);
}

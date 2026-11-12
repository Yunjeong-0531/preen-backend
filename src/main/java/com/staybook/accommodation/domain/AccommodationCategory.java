package com.staybook.accommodation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 숙소 카테고리(호텔·리조트, 한옥, 독채·풀빌라 등) 마스터. 초기 데이터는 마이그레이션으로 넣는다. */
@Entity
@Table(name = "accommodation_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccommodationCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int sortOrder;
}

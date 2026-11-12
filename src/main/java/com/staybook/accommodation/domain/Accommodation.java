package com.staybook.accommodation.domain;

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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accommodations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accommodation {

    /** DRAFT: 작성 중, LISTED: 공개 조회에 노출, HIDDEN: 숨김 */
    public enum ListingStatus {
        DRAFT,
        LISTED,
        HIDDEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostBusinessId;

    @Column(nullable = false)
    private Long regionId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    private LocalTime checkInTime;

    @Column(nullable = false)
    private LocalTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ListingStatus listingStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Accommodation create(Long hostBusinessId, Long regionId, Long categoryId, String name, String description,
                                       String address, BigDecimal latitude, BigDecimal longitude,
                                       LocalTime checkInTime, LocalTime checkOutTime, LocalDateTime now) {
        Accommodation a = new Accommodation();
        a.hostBusinessId = hostBusinessId;
        a.regionId = regionId;
        a.categoryId = categoryId;
        a.name = name;
        a.description = description;
        a.address = address;
        a.latitude = latitude;
        a.longitude = longitude;
        a.checkInTime = checkInTime;
        a.checkOutTime = checkOutTime;
        a.listingStatus = ListingStatus.DRAFT;
        a.createdAt = now;
        return a;
    }

    /** null인 항목은 바꾸지 않는다. */
    public void update(Long regionId, Long categoryId, String name, String description, String address, BigDecimal latitude,
                       BigDecimal longitude, LocalTime checkInTime, LocalTime checkOutTime) {
        if (regionId != null) {
            this.regionId = regionId;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (address != null) {
            this.address = address;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        if (checkInTime != null) {
            this.checkInTime = checkInTime;
        }
        if (checkOutTime != null) {
            this.checkOutTime = checkOutTime;
        }
    }

    public void changeListingStatus(ListingStatus listingStatus) {
        this.listingStatus = listingStatus;
    }

    public boolean isListed() {
        return listingStatus == ListingStatus.LISTED;
    }
}

package com.staybook.accommodation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 숙소와 편의시설의 다대다 연결. 복합 PK (accommodation_id, amenity_id). */
@Entity
@Table(name = "accommodation_amenities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccommodationAmenity {

    @EmbeddedId
    private Key id;

    public static AccommodationAmenity of(Long accommodationId, Long amenityId) {
        AccommodationAmenity link = new AccommodationAmenity();
        link.id = new Key(accommodationId, amenityId);
        return link;
    }

    public Long getAccommodationId() {
        return id.getAccommodationId();
    }

    public Long getAmenityId() {
        return id.getAmenityId();
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "accommodation_id")
        private Long accommodationId;

        @Column(name = "amenity_id")
        private Long amenityId;

        public Key(Long accommodationId, Long amenityId) {
            this.accommodationId = accommodationId;
            this.amenityId = amenityId;
        }
    }
}

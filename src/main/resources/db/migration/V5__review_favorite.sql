-- D. 리뷰·찜 (3개 테이블)
-- 기준: docs/StayBook_ERD_1차구현.md 6장

CREATE TABLE reviews (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id   BIGINT      NOT NULL COMMENT '예약 1건당 리뷰 1개',
    accommodation_id BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    spotify_track_id VARCHAR(64) NULL COMMENT 'Spotify 트랙 ID 문자열만 저장. 메타데이터는 저장하지 않는다',
    rating           TINYINT     NOT NULL COMMENT '1~5',
    content          TEXT        NOT NULL,
    host_reply       TEXT        NULL,
    host_replied_at  DATETIME(6) NULL,
    status           VARCHAR(20) NOT NULL COMMENT 'VISIBLE, HIDDEN',
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_reservation UNIQUE (reservation_id),
    KEY idx_reviews_accommodation (accommodation_id, status, id),
    KEY idx_reviews_user (user_id),
    CONSTRAINT fk_reviews_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_reviews_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE review_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    review_id  BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order SMALLINT     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_images_review (review_id, sort_order),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE favorites (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    accommodation_id BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorites_user_accommodation UNIQUE (user_id, accommodation_id),
    KEY idx_favorites_accommodation (accommodation_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_favorites_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- B. 호스트·숙소·객실 (10개 테이블. 재고 2개는 V3)
-- 기준: docs/StayBook_ERD_1차구현.md 4장

CREATE TABLE host_businesses (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    host_user_id             BIGINT       NOT NULL,
    business_name            VARCHAR(100) NOT NULL,
    business_registration_no VARCHAR(20)  NOT NULL COMMENT '숫자 10자리',
    approval_status          VARCHAR(20)  NOT NULL COMMENT 'PENDING, APPROVED, REJECTED',
    created_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_host_businesses_registration_no UNIQUE (business_registration_no),
    KEY idx_host_businesses_user (host_user_id),
    CONSTRAINT fk_host_businesses_user FOREIGN KEY (host_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE regions (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id BIGINT      NULL,
    name      VARCHAR(50) NOT NULL,
    level     TINYINT     NOT NULL COMMENT '1: 시·도, 2: 시·군·구',
    PRIMARY KEY (id),
    KEY idx_regions_parent (parent_id),
    CONSTRAINT fk_regions_parent FOREIGN KEY (parent_id) REFERENCES regions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE accommodations (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    host_business_id BIGINT         NOT NULL,
    region_id        BIGINT         NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    description      TEXT           NULL,
    address          VARCHAR(255)   NOT NULL,
    latitude         DECIMAL(10, 7) NULL,
    longitude        DECIMAL(10, 7) NULL,
    check_in_time    TIME           NOT NULL,
    check_out_time   TIME           NOT NULL,
    listing_status   VARCHAR(20)    NOT NULL COMMENT 'DRAFT, LISTED, HIDDEN',
    created_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_accommodations_host_business (host_business_id),
    KEY idx_accommodations_listing (listing_status, region_id),
    CONSTRAINT fk_accommodations_host_business FOREIGN KEY (host_business_id) REFERENCES host_businesses (id),
    CONSTRAINT fk_accommodations_region FOREIGN KEY (region_id) REFERENCES regions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE accommodation_images (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    accommodation_id BIGINT       NOT NULL,
    image_url        VARCHAR(500) NOT NULL,
    sort_order       INT          NOT NULL,
    PRIMARY KEY (id),
    KEY idx_accommodation_images_accommodation (accommodation_id, sort_order),
    CONSTRAINT fk_accommodation_images_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE amenities (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_amenities_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE accommodation_amenities (
    accommodation_id BIGINT NOT NULL,
    amenity_id       BIGINT NOT NULL,
    PRIMARY KEY (accommodation_id, amenity_id),
    CONSTRAINT fk_accommodation_amenities_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id),
    CONSTRAINT fk_accommodation_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES amenities (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rooms (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    accommodation_id BIGINT         NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    capacity         INT            NOT NULL COMMENT '기준 인원',
    max_capacity     INT            NOT NULL COMMENT '최대 인원',
    extra_person_fee DECIMAL(12, 2) NOT NULL COMMENT '기준 인원 초과 1인 1박당',
    nightly_price    DECIMAL(12, 2) NOT NULL COMMENT '기본가',
    status           VARCHAR(20)    NOT NULL COMMENT 'ACTIVE, INACTIVE',
    PRIMARY KEY (id),
    KEY idx_rooms_accommodation (accommodation_id),
    CONSTRAINT fk_rooms_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE room_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    room_id    BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL,
    PRIMARY KEY (id),
    KEY idx_room_images_room (room_id, sort_order),
    CONSTRAINT fk_room_images_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 환불정책은 수정하지 않고 새 버전을 만든다. 기존 예약이 과거 버전을 참조하기 때문이다.
CREATE TABLE refund_policies (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    accommodation_id BIGINT      NOT NULL,
    version          VARCHAR(20) NOT NULL,
    policy_text      TEXT        NULL,
    active           BOOLEAN     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_refund_policies_accommodation (accommodation_id, active),
    CONSTRAINT fk_refund_policies_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refund_policy_rules (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    refund_policy_id BIGINT        NOT NULL,
    days_before      INT           NOT NULL COMMENT '체크인 N일 전까지',
    refund_rate      DECIMAL(3, 2) NOT NULL COMMENT '0.00 ~ 1.00',
    PRIMARY KEY (id),
    KEY idx_refund_policy_rules_policy (refund_policy_id),
    CONSTRAINT fk_refund_policy_rules_policy FOREIGN KEY (refund_policy_id) REFERENCES refund_policies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 지역 초기 데이터: 시·도 17개와 주요 시·군·구
INSERT INTO regions (id, parent_id, name, level)
VALUES (1, NULL, '서울', 1),
       (2, NULL, '부산', 1),
       (3, NULL, '대구', 1),
       (4, NULL, '인천', 1),
       (5, NULL, '광주', 1),
       (6, NULL, '대전', 1),
       (7, NULL, '울산', 1),
       (8, NULL, '세종', 1),
       (9, NULL, '경기', 1),
       (10, NULL, '강원', 1),
       (11, NULL, '충북', 1),
       (12, NULL, '충남', 1),
       (13, NULL, '전북', 1),
       (14, NULL, '전남', 1),
       (15, NULL, '경북', 1),
       (16, NULL, '경남', 1),
       (17, NULL, '제주', 1);

INSERT INTO regions (id, parent_id, name, level)
VALUES (101, 1, '강남구', 2),
       (102, 1, '종로구', 2),
       (103, 1, '마포구', 2),
       (104, 1, '중구', 2),
       (201, 2, '해운대구', 2),
       (202, 2, '수영구', 2),
       (401, 4, '중구', 2),
       (901, 9, '가평군', 2),
       (902, 9, '수원시', 2),
       (1001, 10, '강릉시', 2),
       (1002, 10, '속초시', 2),
       (1003, 10, '춘천시', 2),
       (1301, 13, '전주시', 2),
       (1401, 14, '여수시', 2),
       (1501, 15, '경주시', 2),
       (1701, 17, '제주시', 2),
       (1702, 17, '서귀포시', 2);

INSERT INTO amenities (name)
VALUES ('와이파이'),
       ('주차'),
       ('조식'),
       ('수영장'),
       ('바비큐'),
       ('주방'),
       ('세탁기'),
       ('에어컨'),
       ('반려동물 동반'),
       ('스파');

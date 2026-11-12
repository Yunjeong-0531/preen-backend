-- B. 재고 (2개 테이블)
-- 기준: docs/StayBook_ERD_1차구현.md 4장

CREATE TABLE room_inventories (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    room_id      BIGINT         NOT NULL,
    stay_date    DATE           NOT NULL,
    total_count  SMALLINT       NOT NULL,
    booked_count SMALLINT       NOT NULL DEFAULT 0 COMMENT '확정',
    held_count   SMALLINT       NOT NULL DEFAULT 0 COMMENT '선점 중',
    price        DECIMAL(12, 2) NOT NULL COMMENT '날짜별 판매가',
    is_closed    BOOLEAN        NOT NULL DEFAULT FALSE COMMENT '판매 중지',
    PRIMARY KEY (id),
    CONSTRAINT uk_room_inventories_room_date UNIQUE (room_id, stay_date),
    CONSTRAINT fk_room_inventories_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_room_inventories_counts CHECK (
        total_count >= 0 AND booked_count >= 0 AND held_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- reservation_id의 FK는 reservations 테이블이 생기는 V4에서 추가한다.
CREATE TABLE inventory_holds (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id    BIGINT      NOT NULL,
    room_inventory_id BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL COMMENT 'HELD, CONFIRMED, RELEASED',
    expires_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_holds_reservation_inventory UNIQUE (reservation_id, room_inventory_id),
    KEY idx_inventory_holds_inventory (room_inventory_id),
    CONSTRAINT fk_inventory_holds_inventory FOREIGN KEY (room_inventory_id) REFERENCES room_inventories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

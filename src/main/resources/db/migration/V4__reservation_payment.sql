-- C. 예약·결제·환불 (6개 테이블)
-- 기준: docs/StayBook_ERD_1차구현.md 5장

CREATE TABLE reservations (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    reservation_no         VARCHAR(32)    NOT NULL COMMENT '고객 노출용 예약번호',
    user_id                BIGINT         NOT NULL,
    room_id                BIGINT         NOT NULL,
    refund_policy_id       BIGINT         NOT NULL,
    check_in               DATE           NOT NULL,
    check_out              DATE           NOT NULL,
    guest_count            SMALLINT       NOT NULL,
    guest_name             VARCHAR(100)   NOT NULL COMMENT '예약자 스냅샷',
    guest_phone            VARCHAR(512)   NULL COMMENT '암호화 저장',
    total_price            DECIMAL(12, 2) NOT NULL,
    status                 VARCHAR(20)    NOT NULL COMMENT 'PENDING, CONFIRMED, CANCELLED, COMPLETED',
    hold_expires_at        DATETIME(6)    NOT NULL COMMENT '결제 제한 시간',
    refund_policy_snapshot JSON           NOT NULL COMMENT '예약 시점의 환불정책과 규칙',
    cancelled_at           DATETIME(6)    NULL,
    created_at             DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_no UNIQUE (reservation_no),
    KEY idx_reservations_user (user_id, id),
    KEY idx_reservations_room (room_id, id),
    KEY idx_reservations_status_hold (status, hold_expires_at),
    KEY idx_reservations_status_checkout (status, check_out),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_reservations_refund_policy FOREIGN KEY (refund_policy_id) REFERENCES refund_policies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reservation_price_items (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT         NOT NULL,
    item_type      VARCHAR(20)    NOT NULL COMMENT 'ROOM, EXTRA_PERSON',
    name           VARCHAR(100)   NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reservation_price_items_reservation (reservation_id),
    CONSTRAINT fk_reservation_price_items_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reservation_status_history (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id  BIGINT      NOT NULL,
    changed_by      BIGINT      NULL COMMENT 'NULL이면 시스템',
    previous_status VARCHAR(20) NULL COMMENT '예약 생성 시 NULL',
    new_status      VARCHAR(20) NOT NULL,
    reason          TEXT        NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reservation_status_history_reservation (reservation_id, id),
    CONSTRAINT fk_reservation_status_history_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_reservation_status_history_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payments (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT         NOT NULL,
    order_id       VARCHAR(64)    NOT NULL COMMENT '멱등키',
    pg_provider    VARCHAR(30)    NOT NULL,
    payment_key    VARCHAR(200)   NULL COMMENT 'PG사 결제 식별자. 승인 전에는 NULL',
    amount         DECIMAL(12, 2) NOT NULL,
    method         VARCHAR(30)    NULL,
    status         VARCHAR(20)    NOT NULL COMMENT 'READY, PAID, FAILED, REFUNDED',
    paid_at        DATETIME(6)    NULL,
    created_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    KEY idx_payments_reservation (reservation_id, status),
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payment_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT       NOT NULL,
    event_type        VARCHAR(50)  NOT NULL,
    provider_event_id VARCHAR(100) NULL COMMENT '웹훅 중복 방지. 우리가 호출한 승인·취소의 응답은 NULL',
    response_code     VARCHAR(50)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_logs_provider_event UNIQUE (provider_event_id),
    KEY idx_payment_logs_payment (payment_id, id),
    CONSTRAINT fk_payment_logs_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refunds (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id     BIGINT         NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL COMMENT '환불액',
    penalty_amount DECIMAL(12, 2) NOT NULL COMMENT '위약금',
    reason         VARCHAR(255)   NULL,
    status         VARCHAR(20)    NOT NULL COMMENT 'REQUESTED, COMPLETED, FAILED',
    refunded_at    DATETIME(6)    NULL,
    PRIMARY KEY (id),
    KEY idx_refunds_payment (payment_id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- V3에서 미뤄 둔 inventory_holds.reservation_id FK.
-- 이 시점 이전에는 예약이 존재할 수 없으므로, 남아 있는 선점은 유효한 예약을 가리키지 않는다.
-- FK를 걸기 전에 비우고 재고 카운트도 함께 되돌린다.
DELETE FROM inventory_holds;
UPDATE room_inventories SET held_count = 0, booked_count = 0 WHERE held_count <> 0 OR booked_count <> 0;

ALTER TABLE inventory_holds
    ADD CONSTRAINT fk_inventory_holds_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id);

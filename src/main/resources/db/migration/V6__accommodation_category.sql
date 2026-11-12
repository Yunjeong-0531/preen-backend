-- 숙소 카테고리(숙박 유형). 1차 ERD에는 없던 테이블로, 요구사항 변경(유형별 검색 필터)으로 추가한다.
-- 숙소 하나는 카테고리 하나에 속한다.

CREATE TABLE accommodation_categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL COMMENT '목록에 보여 주는 순서',
    PRIMARY KEY (id),
    CONSTRAINT uk_accommodation_categories_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO accommodation_categories (id, name, sort_order)
VALUES (1, '호텔·리조트', 1),
       (2, '펜션', 2),
       (3, '한옥', 3),
       (4, '독채·풀빌라', 4),
       (5, '게스트하우스', 5),
       (6, '모텔', 6),
       (7, '캠핑·글램핑', 7),
       (8, '기타', 99);

-- 이미 등록된 숙소는 유형을 알 수 없으므로 '기타'로 둔다. 호스트가 수정 API로 바꿀 수 있다.
ALTER TABLE accommodations
    ADD COLUMN category_id BIGINT NULL AFTER region_id;

UPDATE accommodations
SET category_id = 8
WHERE category_id IS NULL;

ALTER TABLE accommodations
    MODIFY COLUMN category_id BIGINT NOT NULL,
    ADD KEY idx_accommodations_category (listing_status, category_id),
    ADD CONSTRAINT fk_accommodations_category FOREIGN KEY (category_id) REFERENCES accommodation_categories (id);

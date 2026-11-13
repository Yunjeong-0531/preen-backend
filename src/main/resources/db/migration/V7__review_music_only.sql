-- 리뷰를 별점 + 음악만 남기는 형태로 바꾼다.
-- 리뷰 글, 리뷰 사진, 호스트 답글을 없애고 Spotify 트랙을 필수로 한다.

DROP TABLE review_images;

-- 음악이 없는 리뷰는 새 규칙에 맞지 않으므로 지운다
DELETE FROM reviews WHERE spotify_track_id IS NULL;

ALTER TABLE reviews
    DROP COLUMN content,
    DROP COLUMN host_reply,
    DROP COLUMN host_replied_at,
    MODIFY spotify_track_id VARCHAR(64) NOT NULL COMMENT 'Spotify 트랙 ID 문자열만 저장. 메타데이터는 저장하지 않는다',
    ADD KEY idx_reviews_accommodation_rating (accommodation_id, status, rating, id);

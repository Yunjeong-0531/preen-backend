-- A. 회원·인증·약관 (9개 테이블)
-- 기준: docs/StayBook_ERD_1차구현.md 3장

CREATE TABLE users (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    role       VARCHAR(20) NOT NULL COMMENT 'GUEST, HOST, ADMIN',
    status     VARCHAR(20) NOT NULL COMMENT 'ACTIVE, LOCKED, WITHDRAWN',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL COMMENT '탈퇴 시각 (soft delete)',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_credentials (
    user_id             BIGINT       NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    password_changed_at DATETIME(6)  NOT NULL,
    failed_login_count  SMALLINT     NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)  NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_personal_info (
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(512) NULL COMMENT '암호화 저장',
    email_hash CHAR(64)     NULL COMMENT '로그인 조회용',
    phone      VARCHAR(512) NULL COMMENT '암호화 저장',
    phone_hash CHAR(64)     NULL COMMENT '중복 확인용',
    birth_date DATE         NULL COMMENT '성인 여부 판정용. 소셜 가입 미완료면 NULL',
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_personal_info_email_hash UNIQUE (email_hash),
    KEY idx_user_personal_info_phone_hash (phone_hash),
    CONSTRAINT fk_user_personal_info_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_social_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL COMMENT 'KAKAO, NAVER',
    provider_user_id VARCHAR(100) NOT NULL,
    connected_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_social_accounts_provider UNIQUE (provider, provider_user_id),
    KEY idx_user_social_accounts_user (user_id),
    CONSTRAINT fk_user_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE login_attempts (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NULL COMMENT '없는 계정으로 시도하면 NULL',
    login_id_hash CHAR(64)    NOT NULL,
    ip_address    VARCHAR(45) NULL,
    result        VARCHAR(30) NOT NULL,
    attempted_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_login_attempts_login_id (login_id_hash, attempted_at),
    KEY idx_login_attempts_user (user_id),
    CONSTRAINT fk_login_attempts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 가입 전에도 쓰이므로 users와 FK가 없다 (의도된 설계)
CREATE TABLE verification_codes (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    target_type   VARCHAR(10) NOT NULL COMMENT 'EMAIL, PHONE',
    target_hash   CHAR(64)    NOT NULL,
    purpose       VARCHAR(10) NOT NULL COMMENT 'SIGNUP, RESET',
    code_hash     CHAR(64)    NOT NULL,
    attempt_count SMALLINT    NOT NULL DEFAULT 0,
    expires_at    DATETIME(6) NOT NULL,
    verified_at   DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_verification_codes_target (target_hash, purpose, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    KEY idx_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE terms (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    type         VARCHAR(30) NOT NULL,
    version      VARCHAR(20) NOT NULL,
    content      TEXT        NOT NULL,
    required     BOOLEAN     NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_term_agreements (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    user_id   BIGINT      NOT NULL,
    term_id   BIGINT      NOT NULL,
    is_agreed BOOLEAN     NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_term_agreements UNIQUE (user_id, term_id),
    CONSTRAINT fk_user_term_agreements_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_term_agreements_term FOREIGN KEY (term_id) REFERENCES terms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 초기 약관 (본문은 자리 표시용이며 실제 약관으로 교체해야 한다)
INSERT INTO terms (type, version, content, required, effective_at)
VALUES ('SERVICE', '1.0', 'StayBook 서비스 이용약관 (v1.0)', TRUE, '2026-01-01 00:00:00'),
       ('PRIVACY', '1.0', 'StayBook 개인정보 수집 및 이용 동의 (v1.0)', TRUE, '2026-01-01 00:00:00'),
       ('MARKETING', '1.0', 'StayBook 마케팅 정보 수신 동의 (v1.0)', FALSE, '2026-01-01 00:00:00');

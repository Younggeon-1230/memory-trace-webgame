-- 오박사와 메타몽의 사라진 기억 : 테이블 생성 (MySQL 8.0.16 이상)
CREATE DATABASE IF NOT EXISTS metamong_case DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE metamong_case;

-- 1. 회원
CREATE TABLE users (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT,
    login_id       VARCHAR(30)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    nickname       VARCHAR(20)  NOT NULL,
    role           VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_login_id (login_id),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB;

-- 2. 포켓몬 캐시
CREATE TABLE pokemon (
    pokemon_id   INT          NOT NULL,
    name_en      VARCHAR(30)  NOT NULL,
    name_ko      VARCHAR(30)  NOT NULL,
    height_dm    INT          NOT NULL,
    weight_hg    INT          NOT NULL,
    type1        VARCHAR(20)  NOT NULL,
    type2        VARCHAR(20)  NULL,
    color        VARCHAR(20)  NOT NULL,
    shape        VARCHAR(20)  NOT NULL,
    sprite_path  VARCHAR(255) NOT NULL,
    synced_at    DATETIME     NOT NULL,
    PRIMARY KEY (pokemon_id),
    UNIQUE KEY uk_pokemon_name_en (name_en),
    CONSTRAINT ck_pokemon_height CHECK (height_dm > 0),
    CONSTRAINT ck_pokemon_weight CHECK (weight_hg > 0)
) ENGINE=InnoDB;

-- 3. 포켓몬 특징
CREATE TABLE pokemon_trait (
    trait_id        BIGINT       NOT NULL AUTO_INCREMENT,
    pokemon_id      INT          NOT NULL,
    trait_code      VARCHAR(40)  NOT NULL,
    description     VARCHAR(255) NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,
    source_text     TEXT         NULL,
    source_version  VARCHAR(30)  NULL,
    is_judgment     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (trait_id),
    UNIQUE KEY uk_trait_pokemon_code (pokemon_id, trait_code),
    CONSTRAINT fk_trait_pokemon FOREIGN KEY (pokemon_id) REFERENCES pokemon (pokemon_id),
    CONSTRAINT ck_trait_source CHECK (source_type IN ('POKEDEX', 'CASE_RULE'))
) ENGINE=InnoDB;

-- 4. 사건
CREATE TABLE case_file (
    case_id            BIGINT       NOT NULL AUTO_INCREMENT,
    case_code          VARCHAR(10)  NOT NULL,
    title              VARCHAR(50)  NOT NULL,
    subtitle           VARCHAR(100) NULL,
    briefing           TEXT         NOT NULL,
    scene_image_path   VARCHAR(255) NOT NULL,
    answer_pokemon_id  INT          NOT NULL,
    truth_text         TEXT         NOT NULL,
    motive_text        TEXT         NOT NULL,
    twist_text         TEXT         NOT NULL,
    display_order      INT          NOT NULL,
    is_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    validated_at       DATETIME     NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_case_code (case_code),
    CONSTRAINT fk_case_answer FOREIGN KEY (answer_pokemon_id) REFERENCES pokemon (pokemon_id),
    CONSTRAINT ck_case_order CHECK (display_order >= 0)
) ENGINE=InnoDB;

-- 5. 사건별 용의자
CREATE TABLE case_suspect (
    case_suspect_id  BIGINT       NOT NULL AUTO_INCREMENT,
    case_id          BIGINT       NOT NULL,
    pokemon_id       INT          NOT NULL,
    suspicion_text   VARCHAR(255) NULL,
    display_order    INT          NOT NULL,
    PRIMARY KEY (case_suspect_id),
    UNIQUE KEY uk_suspect_case_pokemon (case_id, pokemon_id),
    CONSTRAINT fk_suspect_case FOREIGN KEY (case_id) REFERENCES case_file (case_id),
    CONSTRAINT fk_suspect_pokemon FOREIGN KEY (pokemon_id) REFERENCES pokemon (pokemon_id),
    CONSTRAINT ck_suspect_order CHECK (display_order >= 0)
) ENGINE=InnoDB;

-- 6. 판정 규칙 (흔적 사전)
CREATE TABLE evidence_rule (
    rule_id          BIGINT       NOT NULL AUTO_INCREMENT,
    condition_type   VARCHAR(20)  NOT NULL,
    condition_value  VARCHAR(40)  NOT NULL,
    basis_text       VARCHAR(255) NOT NULL,
    source_type      VARCHAR(20)  NOT NULL,
    source_ref       VARCHAR(100) NULL,
    PRIMARY KEY (rule_id),
    CONSTRAINT ck_rule_condition CHECK (condition_type IN
        ('HAS_TYPE', 'NOT_TYPE', 'MAX_HEIGHT', 'MAX_WEIGHT', 'SHAPE_IS', 'COLOR_IS', 'NOT_TRAIT')),
    CONSTRAINT ck_rule_source CHECK (source_type IN ('API', 'POKEDEX', 'CASE_RULE'))
) ENGINE=InnoDB;

-- 7. 단서
CREATE TABLE clue (
    clue_id        BIGINT       NOT NULL AUTO_INCREMENT,
    case_id        BIGINT       NOT NULL,
    clue_code      VARCHAR(10)  NOT NULL,
    clue_kind      VARCHAR(10)  NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    summary        VARCHAR(100) NOT NULL,
    detail_text    TEXT         NOT NULL,
    rebuttal_text  VARCHAR(255) NULL,
    rule_id        BIGINT       NULL,
    is_core        BOOLEAN      NOT NULL DEFAULT FALSE,
    area_x         DECIMAL(5,4) NULL,
    area_y         DECIMAL(5,4) NULL,
    area_w         DECIMAL(5,4) NULL,
    area_h         DECIMAL(5,4) NULL,
    display_order  INT          NOT NULL,
    PRIMARY KEY (clue_id),
    UNIQUE KEY uk_clue_case_code (case_id, clue_code),
    CONSTRAINT fk_clue_case FOREIGN KEY (case_id) REFERENCES case_file (case_id),
    CONSTRAINT fk_clue_rule FOREIGN KEY (rule_id) REFERENCES evidence_rule (rule_id),
    CONSTRAINT ck_clue_kind CHECK (clue_kind IN ('SCENE', 'INFERENCE')),
    CONSTRAINT ck_clue_area_range CHECK (
        (area_x IS NULL OR area_x BETWEEN 0 AND 1) AND (area_y IS NULL OR area_y BETWEEN 0 AND 1) AND
        (area_w IS NULL OR area_w BETWEEN 0 AND 1) AND (area_h IS NULL OR area_h BETWEEN 0 AND 1)),
    CONSTRAINT ck_clue_area_by_kind CHECK (
        (clue_kind = 'SCENE' AND area_x IS NOT NULL AND area_y IS NOT NULL AND area_w IS NOT NULL AND area_h IS NOT NULL)
        OR (clue_kind = 'INFERENCE' AND area_x IS NULL AND area_y IS NULL AND area_w IS NULL AND area_h IS NULL)),
    CONSTRAINT ck_clue_order CHECK (display_order >= 0)
) ENGINE=InnoDB;

-- 8. 추론 선행 조건
CREATE TABLE clue_prerequisite (
    prerequisite_id   BIGINT NOT NULL AUTO_INCREMENT,
    clue_id           BIGINT NOT NULL,
    required_clue_id  BIGINT NOT NULL,
    PRIMARY KEY (prerequisite_id),
    UNIQUE KEY uk_prereq_pair (clue_id, required_clue_id),
    CONSTRAINT fk_prereq_clue FOREIGN KEY (clue_id) REFERENCES clue (clue_id),
    CONSTRAINT fk_prereq_required FOREIGN KEY (required_clue_id) REFERENCES clue (clue_id),
    CONSTRAINT ck_prereq_not_self CHECK (clue_id <> required_clue_id)
) ENGINE=InnoDB;

-- 9. 게임 세션
CREATE TABLE play_session (
    session_id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                BIGINT      NOT NULL,
    case_id                BIGINT      NOT NULL,
    status                 VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS',
    score                  INT         NOT NULL DEFAULT 100,
    wrong_exclusion_count  INT         NOT NULL DEFAULT 0,
    accusation_count       INT         NOT NULL DEFAULT 0,
    bonus_awarded          BOOLEAN     NOT NULL DEFAULT FALSE,
    grade                  VARCHAR(2)  NULL,
    active_flag            TINYINT     NULL DEFAULT 1,
    started_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at               DATETIME    NULL,
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_session_active (user_id, case_id, active_flag),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_session_case FOREIGN KEY (case_id) REFERENCES case_file (case_id),
    CONSTRAINT ck_session_status CHECK (status IN ('IN_PROGRESS', 'SOLVED', 'FAILED')),
    CONSTRAINT ck_session_score CHECK (score >= 0),
    CONSTRAINT ck_session_wrong CHECK (wrong_exclusion_count >= 0),
    CONSTRAINT ck_session_accuse CHECK (accusation_count BETWEEN 0 AND 2),
    CONSTRAINT ck_session_grade CHECK (grade IS NULL OR grade IN ('S', 'A', 'B')),
    CONSTRAINT ck_session_active CHECK (
        (status = 'IN_PROGRESS' AND active_flag = 1 AND ended_at IS NULL)
        OR (status <> 'IN_PROGRESS' AND active_flag IS NULL AND ended_at IS NOT NULL))
) ENGINE=InnoDB;

-- 10. 수집한 단서
CREATE TABLE session_clue (
    session_clue_id  BIGINT   NOT NULL AUTO_INCREMENT,
    session_id       BIGINT   NOT NULL,
    clue_id          BIGINT   NOT NULL,
    collected_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_clue_id),
    UNIQUE KEY uk_session_clue (session_id, clue_id),
    CONSTRAINT fk_sclue_session FOREIGN KEY (session_id) REFERENCES play_session (session_id),
    CONSTRAINT fk_sclue_clue FOREIGN KEY (clue_id) REFERENCES clue (clue_id)
) ENGINE=InnoDB;

-- 11. 세션별 용의자 상태
CREATE TABLE session_suspect (
    session_suspect_id  BIGINT      NOT NULL AUTO_INCREMENT,
    session_id          BIGINT      NOT NULL,
    case_suspect_id     BIGINT      NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    basis_clue_id       BIGINT      NULL,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_suspect_id),
    UNIQUE KEY uk_session_suspect (session_id, case_suspect_id),
    CONSTRAINT fk_ssuspect_session FOREIGN KEY (session_id) REFERENCES play_session (session_id),
    CONSTRAINT fk_ssuspect_suspect FOREIGN KEY (case_suspect_id) REFERENCES case_suspect (case_suspect_id),
    CONSTRAINT fk_ssuspect_basis FOREIGN KEY (basis_clue_id) REFERENCES clue (clue_id),
    CONSTRAINT ck_ssuspect_status CHECK (status IN
        ('ACTIVE', 'EXCLUDED_BY_EVIDENCE', 'ELIMINATED_BY_WRONG_ACCUSATION')),
    CONSTRAINT ck_ssuspect_basis CHECK (
        (status = 'EXCLUDED_BY_EVIDENCE' AND basis_clue_id IS NOT NULL)
        OR (status <> 'EXCLUDED_BY_EVIDENCE' AND basis_clue_id IS NULL))
) ENGINE=InnoDB;

-- 12. 행동 기록
CREATE TABLE session_action (
    action_id        BIGINT      NOT NULL AUTO_INCREMENT,
    session_id       BIGINT      NOT NULL,
    action_type      VARCHAR(12) NOT NULL,
    clue_id          BIGINT      NULL,
    case_suspect_id  BIGINT      NULL,
    result           VARCHAR(10) NOT NULL,
    score_delta      INT         NOT NULL DEFAULT 0,
    dedup_key        VARCHAR(60) NULL,
    request_id       CHAR(36)    NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (action_id),
    UNIQUE KEY uk_action_request (request_id),
    UNIQUE KEY uk_action_dedup (session_id, dedup_key),
    CONSTRAINT fk_action_session FOREIGN KEY (session_id) REFERENCES play_session (session_id),
    CONSTRAINT fk_action_clue FOREIGN KEY (clue_id) REFERENCES clue (clue_id),
    CONSTRAINT fk_action_suspect FOREIGN KEY (case_suspect_id) REFERENCES case_suspect (case_suspect_id),
    CONSTRAINT ck_action_type CHECK (action_type IN ('INVESTIGATE', 'INFER', 'EXCLUDE', 'ACCUSE')),
    CONSTRAINT ck_action_result CHECK (result IN ('SUCCESS', 'FAIL', 'REJECTED')),
    CONSTRAINT ck_action_rejected CHECK (result <> 'REJECTED' OR (dedup_key IS NULL AND score_delta = 0)),
    CONSTRAINT ck_action_infer CHECK (action_type <> 'INFER' OR request_id IS NULL)
) ENGINE=InnoDB;

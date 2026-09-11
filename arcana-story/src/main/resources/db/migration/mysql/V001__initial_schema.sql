-- ArcanaStory 초기 스키마 (MySQL 8).
-- 모든 시각은 epoch milliseconds(UTC) BIGINT, UUID는 CHAR(36) 문자열이다.
-- 재실행 가능하도록 모든 테이블은 IF NOT EXISTS로 만든다.

-- ─── StoryHistory (영구) ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS arcanastory_player (
    player_uuid        CHAR(36) NOT NULL,
    created_at         BIGINT   NOT NULL,
    updated_at         BIGINT   NOT NULL,
    legacy_imported_at BIGINT   NULL,
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_chapter_completion (
    player_uuid        CHAR(36)     NOT NULL,
    chapter_id         VARCHAR(191) NOT NULL,
    first_completed_at BIGINT       NOT NULL,
    last_completed_at  BIGINT       NOT NULL,
    completion_count   INT          NOT NULL,
    first_run_id       CHAR(36)     NULL,
    last_run_id        CHAR(36)     NULL,
    PRIMARY KEY (player_uuid, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_branch_completion (
    player_uuid        CHAR(36)     NOT NULL,
    chapter_id         VARCHAR(191) NOT NULL,
    branch_id          VARCHAR(64)  NOT NULL,
    first_completed_at BIGINT       NOT NULL,
    last_completed_at  BIGINT       NOT NULL,
    completion_count   INT          NOT NULL,
    PRIMARY KEY (player_uuid, chapter_id, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- append-only 선택 이력. 같은 run의 같은 선택은 한 번만 기록된다.
CREATE TABLE IF NOT EXISTS arcanastory_choice_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    player_uuid      CHAR(36)     NOT NULL,
    chapter_id       VARCHAR(191) NOT NULL,
    choice_id        VARCHAR(64)  NOT NULL,
    option_id        VARCHAR(64)  NOT NULL,
    run_id           CHAR(36)     NOT NULL,
    decided_by_uuid  CHAR(36)     NOT NULL,
    participant_role VARCHAR(16)  NOT NULL,
    committed_at     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_choice_event_run (player_uuid, run_id, chapter_id, choice_id),
    KEY idx_choice_event_player (player_uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_canonical_choice (
    player_uuid CHAR(36)     NOT NULL,
    chapter_id  VARCHAR(191) NOT NULL,
    choice_id   VARCHAR(64)  NOT NULL,
    option_id   VARCHAR(64)  NOT NULL,
    run_id      CHAR(36)     NOT NULL,
    updated_at  BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, chapter_id, choice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_flag (
    player_uuid CHAR(36)     NOT NULL,
    flag        VARCHAR(64)  NOT NULL,
    value       VARCHAR(255) NOT NULL,
    run_id      CHAR(36)     NULL,
    updated_at  BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ─── Run 감사 / 멱등 commit ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS arcanastory_run (
    run_id              CHAR(36)     NOT NULL,
    chapter_id          VARCHAR(191) NOT NULL,
    content_hash        VARCHAR(64)  NOT NULL,
    participants_json   JSON         NOT NULL,
    leader_history_json JSON         NOT NULL,
    started_at          BIGINT       NOT NULL,
    ended_at            BIGINT       NULL,
    end_reason          VARCHAR(32)  NULL,
    PRIMARY KEY (run_id),
    KEY idx_run_chapter (chapter_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 참가자별 Chapter 완료 commit이 이미 적용되었는지 표시한다. 재시도해도 History가 두 번 반영되지 않는다.
CREATE TABLE IF NOT EXISTS arcanastory_run_commit (
    run_id       CHAR(36) NOT NULL,
    player_uuid  CHAR(36) NOT NULL,
    committed_at BIGINT   NOT NULL,
    PRIMARY KEY (run_id, player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_admin_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor        VARCHAR(64)  NOT NULL,
    target_uuid  CHAR(36)     NULL,
    action       VARCHAR(64)  NOT NULL,
    reason       VARCHAR(512) NOT NULL,
    payload_json JSON         NULL,
    created_at   BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_target (target_uuid, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ─── 보상: 권리(claim)와 실제 지급(delivery outbox) ─────────────────────

CREATE TABLE IF NOT EXISTS arcanastory_reward_claim (
    player_uuid CHAR(36)     NOT NULL,
    reward_key  VARCHAR(320) NOT NULL,
    source      VARCHAR(16)  NOT NULL,
    chapter_id  VARCHAR(191) NULL,
    run_id      CHAR(36)     NULL,
    claimed_at  BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, reward_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_reward_delivery (
    delivery_id     CHAR(36)     NOT NULL,
    player_uuid     CHAR(36)     NOT NULL,
    reward_key      VARCHAR(320) NOT NULL,
    component_index INT          NOT NULL,
    handler         VARCHAR(32)  NOT NULL,
    payload_json    JSON         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempt_id      CHAR(36)     NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(512) NULL,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    delivered_at    BIGINT       NULL,
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uq_delivery_component (player_uuid, reward_key, component_index),
    KEY idx_delivery_player_status (player_uuid, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS arcanastory_reward_delivery_attempt (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    delivery_id CHAR(36)     NOT NULL,
    attempt_id  CHAR(36)     NULL,
    event       VARCHAR(32)  NOT NULL,
    detail      VARCHAR(512) NULL,
    created_at  BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_delivery_attempt_delivery (delivery_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ─── 중단 복구용 snapshot ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS arcanastory_session_snapshot (
    player_uuid           CHAR(36)     NOT NULL,
    run_id                CHAR(36)     NOT NULL,
    chapter_id            VARCHAR(191) NOT NULL,
    content_hash          VARCHAR(64)  NOT NULL,
    state                 VARCHAR(16)  NOT NULL,
    checkpoint_id         VARCHAR(64)  NULL,
    step_id               VARCHAR(64)  NOT NULL,
    location_json         JSON         NULL,
    temp_choices_json     JSON         NOT NULL,
    temp_flags_json       JSON         NOT NULL,
    pending_branches_json JSON         NOT NULL,
    quest_segment_json    JSON         NOT NULL,
    participant_role      VARCHAR(16)  NOT NULL,
    updated_at            BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ENTRY(Chapter 시작 시점)와 CHECKPOINT(가장 최근 checkpoint) 인벤토리. payload 형식은 payload_format이 결정한다.
CREATE TABLE IF NOT EXISTS arcanastory_inventory_snapshot (
    player_uuid    CHAR(36)    NOT NULL,
    kind           VARCHAR(16) NOT NULL,
    run_id         CHAR(36)    NOT NULL,
    checkpoint_id  VARCHAR(64) NULL,
    payload_format VARCHAR(32) NOT NULL,
    data_version   INT         NOT NULL,
    payload        MEDIUMBLOB  NOT NULL,
    checksum       CHAR(64)    NOT NULL,
    xp_level       INT         NOT NULL,
    xp_progress    FLOAT       NOT NULL,
    captured_at    BIGINT      NOT NULL,
    PRIMARY KEY (player_uuid, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

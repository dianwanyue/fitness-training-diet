-- =====================================================================
-- FTDMS · H2 演示库建表脚本（仅用于 h2 profile：无 MySQL 时的一键试跑）
-- ---------------------------------------------------------------------
-- 与 db/schema.sql 的表、字段、唯一约束一一对应，差异仅在于：
--   1. 去掉 MySQL 专有的 ENGINE / CHARSET / COLLATE / COMMENT / ON UPDATE 子句；
--   2. JSON 字段降级为 VARCHAR(4000)（H2 无 MySQL JSON 类型）；
--   3. 唯一约束与普通索引改为独立的 CREATE UNIQUE INDEX / CREATE INDEX。
-- 正式环境（验收、部署）必须使用 db/schema.sql + MySQL 8.4。
-- =====================================================================

CREATE TABLE IF NOT EXISTS user_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    nickname      VARCHAR(50),
    gender        VARCHAR(10),
    birth_date    DATE,
    height_cm     DECIMAL(5,2),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON user_account (username);
CREATE INDEX IF NOT EXISTS idx_user_status ON user_account (status);

CREATE TABLE IF NOT EXISTS daily_goal (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    effective_date DATE         NOT NULL,
    kcal           DECIMAL(8,2) NOT NULL,
    protein_g      DECIMAL(8,2) NOT NULL DEFAULT 0,
    carb_g         DECIMAL(8,2) NOT NULL DEFAULT 0,
    fat_g          DECIMAL(8,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_goal_user_date ON daily_goal (user_id, effective_date);
CREATE INDEX IF NOT EXISTS idx_goal_date ON daily_goal (effective_date);

CREATE TABLE IF NOT EXISTS exercise (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    muscle_group VARCHAR(50),
    equipment    VARCHAR(50),
    is_public    TINYINT      NOT NULL DEFAULT 1,
    created_by   BIGINT,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_exercise_name ON exercise (name);

CREATE TABLE IF NOT EXISTS food (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100)  NOT NULL,
    brand            VARCHAR(100),
    kcal_per_100g    DECIMAL(8,2)  NOT NULL DEFAULT 0,
    protein_per_100g DECIMAL(8,2)  NOT NULL DEFAULT 0,
    carb_per_100g    DECIMAL(8,2)  NOT NULL DEFAULT 0,
    fat_per_100g     DECIMAL(8,2)  NOT NULL DEFAULT 0,
    state            VARCHAR(16)   NOT NULL DEFAULT 'RAW',
    source           VARCHAR(255),
    is_public        TINYINT       NOT NULL DEFAULT 1,
    created_by       BIGINT,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_food_name ON food (name);

CREATE TABLE IF NOT EXISTS training_plan (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    planned_date DATE         NOT NULL,
    title        VARCHAR(100),
    status       VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    note         VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_plan_user_date ON training_plan (user_id, planned_date);

CREATE TABLE IF NOT EXISTS plan_item (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    plan_id          BIGINT       NOT NULL,
    exercise_id      BIGINT       NOT NULL,
    target_sets      INT          NOT NULL DEFAULT 0,
    target_reps      INT          NOT NULL DEFAULT 0,
    target_weight_kg DECIMAL(6,2),
    order_no         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_item_plan ON plan_item (plan_id);

CREATE TABLE IF NOT EXISTS workout_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    plan_id      BIGINT,
    performed_at TIMESTAMP    NOT NULL,
    log_date     DATE         NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_log_user_date ON workout_log (user_id, log_date);

CREATE TABLE IF NOT EXISTS workout_set (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    log_id      BIGINT       NOT NULL,
    exercise_id BIGINT       NOT NULL,
    set_no      INT          NOT NULL DEFAULT 1,
    weight_kg   DECIMAL(6,2),
    reps        INT,
    completed   TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_set_log ON workout_set (log_id);

CREATE TABLE IF NOT EXISTS diet_record (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    record_date         DATE          NOT NULL,
    meal                VARCHAR(16)   NOT NULL DEFAULT 'BREAKFAST',
    food_id             BIGINT        NOT NULL,
    grams               DECIMAL(8,2)  NOT NULL,
    snapshot_kcal       DECIMAL(10,2) NOT NULL DEFAULT 0,
    snapshot_protein_g  DECIMAL(10,2) NOT NULL DEFAULT 0,
    snapshot_carb_g     DECIMAL(10,2) NOT NULL DEFAULT 0,
    snapshot_fat_g      DECIMAL(10,2) NOT NULL DEFAULT 0,
    snapshot_source     VARCHAR(255),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_diet_user_date ON diet_record (user_id, record_date);

CREATE TABLE IF NOT EXISTS body_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    record_date DATE         NOT NULL,
    weight_kg   DECIMAL(5,2) NOT NULL,
    note        VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_body_user_date ON body_record (user_id, record_date);

CREATE TABLE IF NOT EXISTS ai_consent (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    scope      VARCHAR(100) NOT NULL DEFAULT 'BODY_DIET_TRAINING_30D',
    granted_at TIMESTAMP    NOT NULL,
    revoked_at TIMESTAMP,
    status     VARCHAR(16)  NOT NULL DEFAULT 'GRANTED',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_consent_user_status ON ai_consent (user_id, status);

CREATE TABLE IF NOT EXISTS ai_report (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    period_start  DATE         NOT NULL,
    period_end    DATE         NOT NULL,
    summary_json  VARCHAR(4000) NOT NULL,
    content       VARCHAR(4000),
    model_name    VARCHAR(100),
    model_version VARCHAR(50),
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_report_user_created ON ai_report (user_id, created_at);

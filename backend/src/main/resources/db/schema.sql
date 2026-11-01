-- =====================================================================
-- 健身训练与饮食管理系统（FTDMS）· 运行时建表脚本（MySQL 5.7 ~ 8.4 / InnoDB / utf8mb4）
-- ---------------------------------------------------------------------
-- 与 backend/test/schema.sql（M1 冻结基线）的差异：
--   1. 全部改为 CREATE TABLE IF NOT EXISTS，去掉 DROP TABLE，使脚本可重复执行；
--   2. 排序规则用 utf8mb4_unicode_ci：5.7 不支持 8.0 才有的 utf8mb4_0900_ai_ci，
--      而 unicode_ci 在 5.7 与 8.x 上均可用，保证"下载即跑"；
--   3. 表、字段、索引、唯一约束与基线一一对应，未做结构改动。
-- 执行时机：应用启动时由 spring.sql.init 自动执行（见 application.yml），
--           也可以手工执行：mysql -u<user> -p <db> < db/schema.sql
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 账户与目标
-- ---------------------------------------------------------------------

-- 1.1 用户账户：role 区分普通用户与管理员，status 控制账户可用性
CREATE TABLE IF NOT EXISTS `user_account` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`       VARCHAR(50)  NOT NULL                COMMENT '登录用户名，唯一',
    `password_hash`  VARCHAR(255) NOT NULL                COMMENT '密码散列值，不存明文',
    `role`           VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
    `nickname`       VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
    `gender`         VARCHAR(10)  DEFAULT NULL            COMMENT '性别：MALE / FEMALE / UNKNOWN',
    `birth_date`     DATE         DEFAULT NULL            COMMENT '出生日期',
    `height_cm`      DECIMAL(5,2) DEFAULT NULL            COMMENT '身高（cm）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态：ACTIVE / DISABLED',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- 1.2 每日能量与营养目标：按 effective_date 保留历史版本，同日重复提交为覆盖
CREATE TABLE IF NOT EXISTS `daily_goal` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL                COMMENT '所属用户',
    `effective_date` DATE         NOT NULL                COMMENT '生效日期，按业务时区归属',
    `kcal`           DECIMAL(8,2) NOT NULL                COMMENT '每日能量目标（kcal）',
    `protein_g`      DECIMAL(8,2) NOT NULL DEFAULT 0      COMMENT '蛋白质目标（g）',
    `carb_g`         DECIMAL(8,2) NOT NULL DEFAULT 0      COMMENT '碳水化合物目标（g）',
    `fat_g`          DECIMAL(8,2) NOT NULL DEFAULT 0      COMMENT '脂肪目标（g）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_goal_user_date` (`user_id`, `effective_date`),
    KEY `idx_goal_date` (`effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日能量与营养目标表';

-- ---------------------------------------------------------------------
-- 2. 基础数据（动作库 / 食物库）
-- ---------------------------------------------------------------------

-- 2.1 动作库：is_public=1 为管理员维护的公共动作，0 为用户自定义
CREATE TABLE IF NOT EXISTS `exercise` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100) NOT NULL                COMMENT '动作名称',
    `muscle_group`  VARCHAR(50)  DEFAULT NULL            COMMENT '主要肌群',
    `equipment`     VARCHAR(50)  DEFAULT NULL            COMMENT '器械类型',
    `is_public`     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否公共动作：1 公共 / 0 个人自定义',
    `created_by`    BIGINT       DEFAULT NULL            COMMENT '创建人（自定义动作时填写）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_exercise_name` (`name`),
    KEY `idx_exercise_public_status` (`is_public`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作库表';

-- 2.2 食物库：营养值统一按每 100 g 标注，并记录来源与生熟状态
CREATE TABLE IF NOT EXISTS `food` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100)  NOT NULL                COMMENT '食物名称',
    `brand`         VARCHAR(100)  DEFAULT NULL            COMMENT '品牌或规格说明',
    `kcal_per_100g`  DECIMAL(8,2) NOT NULL DEFAULT 0      COMMENT '每100g能量（kcal）',
    `protein_per_100g` DECIMAL(8,2) NOT NULL DEFAULT 0    COMMENT '每100g蛋白质（g）',
    `carb_per_100g` DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '每100g碳水化合物（g）',
    `fat_per_100g`  DECIMAL(8,2)  NOT NULL DEFAULT 0      COMMENT '每100g脂肪（g）',
    `state`         VARCHAR(16)   NOT NULL DEFAULT 'RAW'  COMMENT '状态：RAW 生重 / COOKED 熟重 / BRAND 品牌规格',
    `source`        VARCHAR(255)  DEFAULT NULL            COMMENT '营养数据来源（包装标签或资料名称）；接口层新增/修改时必填',
    `is_public`     TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '是否公共食物：1 公共 / 0 个人自定义',
    `created_by`    BIGINT        DEFAULT NULL            COMMENT '创建人',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_food_name` (`name`),
    KEY `idx_food_public_status` (`is_public`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='食物库表（每100g营养值）';

-- ---------------------------------------------------------------------
-- 3. 训练计划与记录
-- ---------------------------------------------------------------------

-- 3.1 训练计划：描述"准备执行"的内容，状态单向流转 PLANNED→COMPLETED/CANCELLED
CREATE TABLE IF NOT EXISTS `training_plan` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL                COMMENT '所属用户',
    `planned_date`  DATE         NOT NULL                COMMENT '计划日期，按业务时区归属',
    `title`         VARCHAR(100) DEFAULT NULL            COMMENT '计划标题',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED 待执行 / COMPLETED 已完成 / CANCELLED 已取消',
    `note`          VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_plan_user_date` (`user_id`, `planned_date`),
    KEY `idx_plan_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练计划表';

-- 3.2 计划动作：一个计划含 1–20 条明细，与动作库关联
CREATE TABLE IF NOT EXISTS `plan_item` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`           BIGINT       NOT NULL                COMMENT '所属计划',
    `exercise_id`       BIGINT       NOT NULL                COMMENT '关联动作库',
    `target_sets`       INT          NOT NULL DEFAULT 0      COMMENT '目标组数',
    `target_reps`       INT          NOT NULL DEFAULT 0      COMMENT '目标每组次数',
    `target_weight_kg`  DECIMAL(6,2) DEFAULT NULL            COMMENT '目标重量（kg）',
    `order_no`          INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    PRIMARY KEY (`id`),
    KEY `idx_item_plan` (`plan_id`),
    KEY `idx_item_exercise` (`exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练计划动作表';

-- 3.3 训练记录：保存"实际完成"情况，plan_id 为空表示临时训练
CREATE TABLE IF NOT EXISTS `workout_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL                COMMENT '所属用户',
    `plan_id`       BIGINT       DEFAULT NULL            COMMENT '关联计划，NULL 表示临时训练',
    `performed_at`  DATETIME     NOT NULL                COMMENT '实际训练时间',
    `log_date`      DATE         NOT NULL                COMMENT '训练归属日期（冗余，便于区间统计）',
    `note`          VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_user_date` (`user_id`, `log_date`),
    KEY `idx_log_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练记录表';

-- 3.4 训练组明细：一条训练记录含 1–50 组
CREATE TABLE IF NOT EXISTS `workout_set` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `log_id`      BIGINT       NOT NULL                COMMENT '所属训练记录',
    `exercise_id` BIGINT       NOT NULL                COMMENT '关联动作库',
    `set_no`      INT          NOT NULL DEFAULT 1      COMMENT '组序号',
    `weight_kg`   DECIMAL(6,2) DEFAULT NULL            COMMENT '实际重量（kg）',
    `reps`        INT          DEFAULT NULL            COMMENT '实际次数',
    `completed`   TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '该组是否完成：1 是 / 0 否',
    PRIMARY KEY (`id`),
    KEY `idx_set_log` (`log_id`),
    KEY `idx_set_exercise` (`exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练组明细表';

-- ---------------------------------------------------------------------
-- 4. 饮食与身体数据
-- ---------------------------------------------------------------------

-- 4.1 饮食记录：保存录入时的四项营养快照，食物库后续变更不影响历史汇总
CREATE TABLE IF NOT EXISTS `diet_record` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT        NOT NULL                COMMENT '所属用户',
    `record_date`       DATE          NOT NULL                COMMENT '记录日期，按业务时区归属',
    `meal`              VARCHAR(16)   NOT NULL DEFAULT 'BREAKFAST' COMMENT '餐次：BREAKFAST 早餐 / LUNCH 午餐 / DINNER 晚餐 / SNACK 加餐',
    `food_id`           BIGINT        NOT NULL                COMMENT '关联食物库',
    `grams`             DECIMAL(8,2)  NOT NULL                COMMENT '实际摄入克重（g）',
    `snapshot_kcal`     DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '快照：本项能量（kcal）= 每100g值 × 克重 ÷ 100',
    `snapshot_protein_g` DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '快照：本项蛋白质（g）',
    `snapshot_carb_g`   DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '快照：本项碳水化合物（g）',
    `snapshot_fat_g`    DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '快照：本项脂肪（g）',
    `snapshot_source`   VARCHAR(255)  DEFAULT NULL            COMMENT '快照：营养数据来源',
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_diet_user_date` (`user_id`, `record_date`),
    KEY `idx_diet_food` (`food_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='饮食记录表（含营养快照）';

-- 4.2 体重记录：同一用户同一日期唯一，重复提交按更新处理
CREATE TABLE IF NOT EXISTS `body_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                COMMENT '所属用户',
    `record_date` DATE         NOT NULL                COMMENT '记录日期，按业务时区归属',
    `weight_kg`   DECIMAL(5,2) NOT NULL                COMMENT '体重（kg）',
    `note`        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_body_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='体重记录表（同日唯一）';

-- ---------------------------------------------------------------------
-- 5. AI 分析：授权与报告（数据层已就位，接口在 W7 实现）
-- ---------------------------------------------------------------------

-- 5.1 分析授权：记录授权范围、授权与撤回时间，撤回后不可再用于生成报告
CREATE TABLE IF NOT EXISTS `ai_consent` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL                COMMENT '所属用户',
    `scope`       VARCHAR(100) NOT NULL DEFAULT 'BODY_DIET_TRAINING_30D' COMMENT '授权范围：身体数据、饮食、训练记录，默认最近30天',
    `granted_at`  DATETIME    NOT NULL                COMMENT '授权时间',
    `revoked_at`  DATETIME    DEFAULT NULL            COMMENT '撤回时间，NULL 表示未撤回',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'GRANTED' COMMENT '状态：GRANTED 已授权 / REVOKED 已撤回',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_consent_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI分析授权表';

-- 5.2 分析报告：保存输入摘要与模型版本，支持追溯、降级标记与删除
CREATE TABLE IF NOT EXISTS `ai_report` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT      NOT NULL                COMMENT '所属用户',
    `period_start`   DATE        NOT NULL                COMMENT '分析区间起始日期',
    `period_end`     DATE        NOT NULL                COMMENT '分析区间结束日期',
    `summary_json`   JSON        NOT NULL                COMMENT '输入模型的原始结构化摘要（用于数值核对与追溯）',
    `content`        TEXT                                 COMMENT '模型生成的分析文本',
    `model_name`     VARCHAR(100) DEFAULT NULL           COMMENT '模型名称，如 Qwen3-4B-Instruct-2507',
    `model_version`  VARCHAR(50)  DEFAULT NULL           COMMENT '模型版本标识',
    `status`         VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS 成功 / FAILED 失败 / DEGRADED 降级仅保留统计',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_report_user_created` (`user_id`, `created_at`),
    KEY `idx_report_period` (`user_id`, `period_start`, `period_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI分析报告表';

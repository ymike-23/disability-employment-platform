-- H2 内存测试库建表脚本（仅测试使用，不作用于开发库 disability_db）
-- 字段与 MySQL 实际表结构保持一致

CREATE TABLE IF NOT EXISTS sys_user (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL UNIQUE,
    password         VARCHAR(255) NOT NULL,
    status           TINYINT DEFAULT 1,
    deleted          TINYINT DEFAULT 0,
    last_login_time  DATETIME,
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name    VARCHAR(50)  NOT NULL,
    role_code    VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255),
    status       TINYINT DEFAULT 1,
    deleted      TINYINT DEFAULT 0,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_grid (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    grid_name        VARCHAR(50)  NOT NULL,
    grid_code        VARCHAR(20),
    district         VARCHAR(20),
    community        VARCHAR(100),
    grid_worker_id   BIGINT,
    grid_worker_name VARCHAR(50),
    description      VARCHAR(255),
    status           TINYINT DEFAULT 1,
    deleted          TINYINT DEFAULT 0,
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS disabled_person (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(50) NOT NULL,
    gender            VARCHAR(10),
    id_card           VARCHAR(18),
    birth_date        DATE,
    phone             VARCHAR(20),
    district          VARCHAR(20),
    grid_id           BIGINT,
    address           VARCHAR(255),
    longitude         DECIMAL(10, 6),
    latitude          DECIMAL(10, 6),
    disability_type   VARCHAR(20),
    disability_level  VARCHAR(20),
    education         VARCHAR(20),
    status            TINYINT DEFAULT 1,
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by         BIGINT,
    update_by         BIGINT
);

-- 独立残疾人证件表，字段对应 DisabledCertPO；不影响生产数据库。
CREATE TABLE IF NOT EXISTS disabled_cert (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id         BIGINT NOT NULL,
    cert_no           VARCHAR(50),
    cert_type         VARCHAR(20),
    issue_date        DATE,
    expire_date       DATE,
    issue_org         VARCHAR(100),
    disability_type   VARCHAR(20),
    disability_level  VARCHAR(20),
    cert_status       VARCHAR(20) DEFAULT '0',
    is_current        TINYINT(1) NOT NULL DEFAULT 1,
    remark            VARCHAR(255),
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_person_id ON disabled_cert (person_id);
CREATE INDEX IF NOT EXISTS idx_cert_no ON disabled_cert (cert_no);
CREATE INDEX IF NOT EXISTS idx_person_current ON disabled_cert (person_id, is_current);

-- 培训需求测试表，仅作用于 H2；按现有 MySQL 结构映射，不迁移生产数据库。
CREATE TABLE IF NOT EXISTS training_demand (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id         BIGINT NOT NULL,
    demand_type       VARCHAR(20),
    demand_desc       VARCHAR(500),
    status            VARCHAR(20) DEFAULT '0',
    remark            VARCHAR(255),
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by         BIGINT,
    update_by         BIGINT
);

-- 培训计划测试表，仅作用于 H2；字段及状态沿用现有 MySQL 结构。
CREATE TABLE IF NOT EXISTS training_plan (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_name         VARCHAR(100) NOT NULL,
    category          VARCHAR(20),
    training_type     VARCHAR(20),
    start_date        DATE,
    end_date          DATE,
    location          VARCHAR(100),
    capacity          INT DEFAULT 0,
    enrolled_count    INT DEFAULT 0,
    organizer         VARCHAR(100),
    status            VARCHAR(20) DEFAULT '0',
    remark            VARCHAR(255),
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by         BIGINT,
    update_by         BIGINT
);

CREATE INDEX IF NOT EXISTS idx_category ON training_plan (category);
CREATE INDEX IF NOT EXISTS idx_status ON training_plan (status);
CREATE INDEX IF NOT EXISTS idx_training_type ON training_plan (training_type);

-- 预置业务角色（与生产一致，GRID_WORKER 保留但不参与鉴权）
-- 使用条件插入保证幂等：多个测试上下文共享同一 H2 内存库时不会重复写入
INSERT INTO sys_role (role_name, role_code, description, status, deleted)
SELECT '系统管理员', 'ADMIN', '系统管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');

INSERT INTO sys_role (role_name, role_code, description, status, deleted)
SELECT '业务人员', 'BUSINESS', '业务人员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'BUSINESS');

INSERT INTO sys_role (role_name, role_code, description, status, deleted)
SELECT '网格员', 'GRID_WORKER', '网格员（保留记录，不参与鉴权）', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'GRID_WORKER');

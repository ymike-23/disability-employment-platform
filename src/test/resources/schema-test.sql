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
    -- 残疾人证信息（当前证件，全部可空：历史档案未录入时保持 NULL）
    cert_no           VARCHAR(32),
    cert_issue_date   DATE,
    cert_expire_date  DATE,
    cert_issue_org    VARCHAR(100),
    cert_status       VARCHAR(10),
    status            TINYINT DEFAULT 1,
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by         BIGINT,
    update_by         BIGINT
);

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
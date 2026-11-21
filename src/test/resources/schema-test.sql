-- H2 内存测试库建表脚本（仅测试使用，不作用于开发库 disability_db）
-- 字段以 MySQL 为基准；training_record 包含待审批增量（status、人员计划唯一约束）。

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

-- 字典测试表及后续测试数据只作用于 H2，不修改生产字典。
CREATE TABLE IF NOT EXISTS sys_dict (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type    VARCHAR(50) NOT NULL,
    dict_label   VARCHAR(50) NOT NULL,
    dict_value   VARCHAR(50) NOT NULL,
    sort_order   INT DEFAULT 0,
    status       TINYINT DEFAULT 1,
    remark       VARCHAR(255),
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_type_value UNIQUE (dict_type, dict_value)
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
    employment_status VARCHAR(20) DEFAULT NULL,
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
    publish_status    VARCHAR(20) NOT NULL DEFAULT '0',
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

-- 仅供H2测试初始化；result为0待考核/1通过/2未通过/3未参加，status独立保留报名状态。
CREATE TABLE IF NOT EXISTS training_record (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id         BIGINT NOT NULL,
    plan_id           BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT '1',
    CONSTRAINT uk_training_record_person_plan UNIQUE (person_id, plan_id),
    enroll_date       DATE,
    score             DECIMAL(5, 2),
    result            VARCHAR(20),
    cert_no           VARCHAR(50),
    assessment_date   DATE,
    acquired_skills   TEXT,
    rehab_effect      TEXT,
    improvement       TEXT,
    remark            VARCHAR(255),
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- H2 索引名称全库唯一，因此加表前缀；索引列与生产表一致。
CREATE INDEX IF NOT EXISTS idx_training_record_person_id ON training_record (person_id);
CREATE INDEX IF NOT EXISTS idx_training_record_plan_id ON training_record (plan_id);

-- 与已核对的实库结构一致；本轮仅同步夹具，不执行初始化SQL。
CREATE TABLE IF NOT EXISTS training_certificate (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    certificate_no      VARCHAR(50) NOT NULL UNIQUE,
    certificate_name    VARCHAR(100) NOT NULL,
    issue_date          DATE NOT NULL,
    person_id           BIGINT NOT NULL,
    training_record_id  BIGINT NOT NULL UNIQUE,
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employment_assessment (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id             BIGINT NOT NULL,
    employment_intention  VARCHAR(20) NOT NULL DEFAULT '3',
    existing_skills       TEXT,
    basic_ability         TEXT,
    support_needs         TEXT,
    development_direction TEXT,
    training_suggestion   VARCHAR(100),
    assessment_summary    TEXT,
    assessment_date       DATE NOT NULL,
    operator_id           BIGINT,
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted               TINYINT NOT NULL DEFAULT 0
);

-- 企业和岗位字段与实库一致；H2 索引名称加表前缀以避免跨表重名。
CREATE TABLE IF NOT EXISTS company (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name     VARCHAR(100) NOT NULL,
    contact_person   VARCHAR(50),
    contact_phone    VARCHAR(20),
    address          VARCHAR(255),
    district         VARCHAR(20),
    industry         VARCHAR(50),
    remark           VARCHAR(500),
    status           TINYINT DEFAULT 1,
    deleted          TINYINT DEFAULT 0,
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by        BIGINT,
    update_by        BIGINT
);

CREATE INDEX IF NOT EXISTS idx_company_name ON company (company_name);
CREATE INDEX IF NOT EXISTS idx_company_district ON company (district);
CREATE INDEX IF NOT EXISTS idx_company_industry ON company (industry);

CREATE TABLE IF NOT EXISTS job (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id             BIGINT NOT NULL,
    job_name               VARCHAR(100) NOT NULL,
    salary                 VARCHAR(50),
    requirement            TEXT,
    education_requirement  VARCHAR(20),
    headcount              INT DEFAULT 1,
    status                 VARCHAR(20) DEFAULT '0',
    remark                 VARCHAR(500),
    deleted                TINYINT DEFAULT 0,
    create_time            DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by              BIGINT,
    update_by              BIGINT
);

CREATE INDEX IF NOT EXISTS idx_job_company_id ON job (company_id);
CREATE INDEX IF NOT EXISTS idx_job_name ON job (job_name);
CREATE INDEX IF NOT EXISTS idx_job_status ON job (status);

-- 对应只读查询到的生产字典值，中文名称仅存于 dict_label。
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT d.dict_type, d.dict_value, d.dict_label, d.sort_order, 1
FROM (VALUES
    ('cert_status', '0', '未办理', 0),
    ('cert_status', '1', '正常', 1),
    ('cert_status', '2', '换证中', 2),
    ('cert_status', '3', '注销', 3),
    ('cert_status', '4', '过期', 4),
    ('cert_type', '1', '第二代残疾人证', 1),
    ('cert_type', '2', '第一代残疾人证', 2),
    ('cert_type', '3', '临时证明', 3),
    ('disability_type', '1', '肢体残疾', 1),
    ('disability_type', '2', '视力残疾', 2),
    ('disability_type', '3', '听力残疾', 3),
    ('disability_type', '4', '言语残疾', 4),
    ('disability_type', '5', '智力残疾', 5),
    ('disability_type', '6', '精神残疾', 6),
    ('disability_type', '7', '多重残疾', 7),
    ('disability_level', '1', '一级', 1),
    ('disability_level', '2', '二级', 2),
    ('disability_level', '3', '三级', 3),
    ('disability_level', '4', '四级', 4),
    ('district', '1', '新城区', 1),
    ('district', '2', '回民区', 2),
    ('district', '3', '玉泉区', 3),
    ('district', '4', '赛罕区', 4),
    ('district', '5', '土默特左旗', 5),
    ('district', '6', '托克托县', 6),
    ('district', '7', '和林格尔县', 7),
    ('district', '8', '清水河县', 8),
    ('district', '9', '武川县', 9),
    ('district', '10', '经济技术开发区', 10),
    ('education', '1', '小学', 1),
    ('education', '2', '初中', 2),
    ('education', '3', '高中', 3),
    ('education', '4', '中专', 4),
    ('education', '5', '大专', 5),
    ('education', '6', '本科', 6),
    ('education', '7', '硕士及以上', 7),
    ('training_category', '1', '康复类', 1),
    ('training_category', '2', '就业类', 2),
    ('training_record_status', '1', '已报名', 1),
    ('training_record_status', '2', '培训中', 2),
    ('training_record_status', '3', '已完成', 3),
    ('training_record_status', '4', '未通过', 4),
    ('training_record_status', '5', '已取消', 5),
    ('training_status', '0', '未开始', 0),
    ('training_status', '1', '报名中', 1),
    ('training_status', '2', '进行中', 2),
    ('training_status', '3', '已结束', 3),
    ('training_status', '4', '已取消', 4),
    ('training_type', '1', '肢体功能康复训练', 1),
    ('training_type', '2', '手部功能训练', 2),
    ('training_type', '3', '语言康复训练', 3),
    ('training_type', '4', '听力康复训练', 4),
    ('training_type', '5', '视力康复训练', 5),
    ('training_type', '6', '精神康复训练', 6),
    ('training_type', '11', '按摩培训', 11),
    ('training_type', '12', '电商培训', 12),
    ('training_type', '13', '手工制作', 13),
    ('training_type', '14', '计算机培训', 14),
    ('training_type', '15', '家政服务', 15),
    ('training_type', '99', '其他', 99),
    ('training_publish_status', '0', '草稿', 0),
    ('training_publish_status', '1', '已发布', 1)
) AS d(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict existing
    WHERE existing.dict_type = d.dict_type AND existing.dict_value = d.dict_value
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

-- 简历测试表复用现有 MySQL 字段，不额外添加唯一约束，验证服务的人行锁防重。
CREATE TABLE IF NOT EXISTS resume (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id         BIGINT NOT NULL,
    job_intention     VARCHAR(255),
    expected_salary   VARCHAR(50),
    self_evaluation   TEXT,
    skills_summary    TEXT,
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_person_id ON resume (person_id);

-- 就业服务测试结构；不设置人员/岗位永久唯一约束，历史人员状态保留 NULL。
CREATE TABLE IF NOT EXISTS employment_service (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id         BIGINT NOT NULL,
    job_id            BIGINT,
    root_service_id   BIGINT,
    service_type      VARCHAR(20),
    status            VARCHAR(20) NOT NULL DEFAULT '0',
    service_date      DATE,
    content           TEXT,
    result            VARCHAR(100),
    remark            VARCHAR(500),
    deleted           TINYINT DEFAULT 0,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by         BIGINT,
    update_by         BIGINT
);

CREATE INDEX IF NOT EXISTS idx_employment_service_root ON employment_service (root_service_id);
CREATE INDEX IF NOT EXISTS idx_employment_service_status ON employment_service (status);
CREATE INDEX IF NOT EXISTS idx_employment_service_person ON employment_service (person_id);
CREATE INDEX IF NOT EXISTS idx_employment_service_job ON employment_service (job_id);
CREATE INDEX IF NOT EXISTS idx_employment_service_type ON employment_service (service_type);
CREATE INDEX IF NOT EXISTS idx_employment_service_date ON employment_service (service_date);

INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order, status)
SELECT d.dict_type, d.dict_value, d.dict_label, d.sort_order, 1
FROM (VALUES
    ('service_type', '1', '岗位推荐', 1),
    ('service_type', '2', '企业联系', 2),
    ('service_type', '3', '面试', 3),
    ('service_type', '4', '入职', 4),
    ('service_type', '5', '回访', 5),
    ('service_type', '6', '录用', 6),
    ('service_type', '7', '离职', 7),
    ('employment_status', '0', '待就业', 0),
    ('employment_status', '1', '求职中', 1),
    ('employment_status', '2', '已就业', 2),
    ('employment_status', '3', '离职', 3),
    ('employment_service_status', '0', '待办', 0),
    ('employment_service_status', '1', '完成', 1),
    ('employment_service_status', '2', '取消', 2),
    ('employment_service_result', '1', '成功', 1),
    ('employment_service_result', '2', '失败', 2),
    ('employment_service_result', '3', '放弃', 3)
) AS d(dict_type, dict_value, dict_label, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict existing
    WHERE existing.dict_type = d.dict_type AND existing.dict_value = d.dict_value
);

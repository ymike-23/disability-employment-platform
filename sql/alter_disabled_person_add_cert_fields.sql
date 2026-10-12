-- ============================================================================
-- 残疾人档案管理：新增"基础残疾人证信息"字段（MySQL 增量 DDL）
-- ============================================================================
-- 目标库：disability_db
-- 目标表：disabled_person
--
-- 【重要】本项目没有 Flyway / Liquibase 等数据库迁移机制，
-- 本文件【不会自动执行】，必须由人工在 MySQL 中执行。
-- 修改 src/test/resources/schema-test.sql 只会影响 H2 内存测试库，
-- 不会、也绝不会自动更新生产库。执行前请先备份。
--
-- 兼容性：全部为新增可空字段，不含默认值变更、不改已有列，
-- 已有档案记录不受影响，未录入证件的历史档案保持 NULL，不会被强制补齐。
-- ============================================================================

ALTER TABLE disabled_person
    ADD COLUMN cert_no          VARCHAR(32)  NULL COMMENT '残疾人证号（与身份证号 id_card 是两个不同概念）' AFTER education,
    ADD COLUMN cert_issue_date  DATE         NULL COMMENT '发证日期' AFTER cert_no,
    ADD COLUMN cert_expire_date DATE         NULL COMMENT '有效期至' AFTER cert_issue_date,
    ADD COLUMN cert_issue_org   VARCHAR(100) NULL COMMENT '发证机关' AFTER cert_expire_date,
    ADD COLUMN cert_status      VARCHAR(10)  NULL COMMENT '证件状态：正常/换证中/注销/挂失（与档案自身状态 status 无关）' AFTER cert_issue_org;

-- ============================================================================
-- 执行后验证（可选）
-- ============================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'disabled_person'
-- ORDER BY ORDINAL_POSITION;

-- ============================================================================
-- 幂等性说明
-- ============================================================================
-- MySQL 不支持 "ADD COLUMN IF NOT EXISTS"。重复执行会报错：
--   ERROR 1060 (42S21): Duplicate column name 'cert_no'
-- 重复执行前请先用上面的 INFORMATION_SCHEMA 查询确认字段是否已存在。

-- ============================================================================
-- 关于 cert_no 唯一索引（必读）
-- ============================================================================
-- 当前业务规则（与现有 id_card 实现保持一致）：
--   查重排除已逻辑删除的记录 => 已逻辑删除档案的证号允许复用。
-- 若在数据库侧建立唯一索引：
--   ALTER TABLE disabled_person ADD UNIQUE KEY uk_cert_no (cert_no);
-- 则该证号在档案被逻辑删除后无法复用，与上述业务规则冲突。
-- 因此本次【不建】唯一索引，仅做应用层查重（与 id_card 现行做法一致）。
--
-- 已知代价：应用层"先查后写"存在并发重复风险（TOCTOU）。
--
-- 若将来业务改为"证号永不复用"，必须同时做两件事（缺一不可）：
--   1) 数据库：ALTER TABLE disabled_person ADD UNIQUE KEY uk_cert_no (cert_no);
--   2) 代码：修改 DisabledPersonServiceImpl#checkCertNoDuplicate，
--      使其查询包含已逻辑删除的记录（移除 @TableLogic 的自动过滤）。
-- 否则数据库约束与业务查重规则会不一致。

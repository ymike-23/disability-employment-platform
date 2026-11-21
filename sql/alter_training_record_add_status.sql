-- 培训记录流程状态与结业结果分离（待确认，禁止自动执行或导入）。
-- 本次 2026-10-22 只读扫描：training_record 无 status 列且无记录；result 是结业结果。
-- 应用中的 status 使用 training_record_status.dict_value，不再写入 result。
-- 本文件不修改 result、score、cert_no 或任何业务数据，不自动转换历史结果。
--
-- 执行前必须重新只读确认：
-- 1. 当前连接是目标 disability_db，且 status 列尚不存在。
-- 2. training_record 总行数为 0（包括逻辑删除记录）。
--    若不为 0，停止执行，先确认历史流程状态与计数的迁移口径，不能默认按已报名回填。
-- 3. training_record_status 的 1/2/3/4/5 字典项均存在且启用。
-- 4. 经用户确认后，在切换应用代码前执行；MySQL DDL 隐式提交，不能靠事务回滚撤销。
--
-- 预检（仅返回元数据或汇总，不输出人员资料）：
-- SELECT COUNT(*) AS record_count FROM disability_db.training_record;
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'training_record' AND COLUMN_NAME = 'status';
-- SELECT dict_value, status FROM disability_db.sys_dict
-- WHERE dict_type = 'training_record_status' ORDER BY sort_order, id;

ALTER TABLE disability_db.training_record
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT '1'
    COMMENT '流程状态（关联training_record_status.dict_value）' AFTER plan_id;

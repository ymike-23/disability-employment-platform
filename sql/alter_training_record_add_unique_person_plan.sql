-- 培训记录"同一人员不可重复报名同一计划"唯一约束
--
-- ⚠️ 本脚本尚未在生产库执行，等待人工确认后再运行。
-- 2026-10-22 实库只读扫描：该联合唯一索引不存在，含逻辑删除的重复组合为 0。
-- 执行前须重新核对索引及重复组合；扫描结果不能替代执行时预检。
-- 本索引不包含 deleted，已取消或逻辑删除的记录仍占用该人员与计划的组合。
-- 建索引可能等待元数据锁，MySQL DDL 隐式提交；禁止自动执行或自动清理重复记录。
--
-- 业务规则：
--   1. 同一个残疾人对于同一个培训计划，只允许有一条报名记录（含 status=5 已取消）。
--   2. 逻辑删除后仍然不能重新创建相同人员与计划的报名记录（已明确接受）。
--   3. 恢复报名应修改原记录的状态，而不是重新 INSERT。
--   4. Service 层已做显式校验并返回 409，唯一约束仅作为并发兜底。
--
-- 前置检查（必须先执行，确认无重复数据）：
--   SELECT person_id, plan_id, COUNT(*) AS cnt
--   FROM disability_db.training_record
--   GROUP BY person_id, plan_id
--   HAVING COUNT(*) > 1;
--
--   若上述查询返回任何行，请先报告，由人工决定保留哪一条记录，
--   不要自动删除历史记录。

-- 1. 唯一约束：人员 + 计划
ALTER TABLE disability_db.training_record
    ADD UNIQUE KEY uk_person_plan (person_id, plan_id);

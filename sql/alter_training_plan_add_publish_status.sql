-- 培训计划发布状态字段（台账留档）
--
-- 说明：
--   本脚本已于 production 库 disability_db 手工执行完成，
--   training_plan.publish_status 与 sys_dict.training_publish_status 均已存在。
--   请勿重复执行；此处仅作为变更留档，供重建环境或答辩材料使用。
--
-- 职责划分：
--   publish_status  控制计划是否对外公开（0 草稿 / 1 已发布）
--   status          控制培训业务进度（0 未开始 / 1 报名中 / 2 进行中 / 3 已结束 / 4 已取消）
--
--   两者互不替代：取消计划仍使用 status=4，不引入"撤回发布"状态。

-- 1. 新增发布状态字段
ALTER TABLE training_plan
    ADD COLUMN publish_status VARCHAR(20) NOT NULL DEFAULT '0' COMMENT '发布状态：0草稿 1已发布' AFTER status;

-- 2. 发布状态字典（新增前先确认不存在，避免重复插入）
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order, status, remark)
SELECT 'training_publish_status', '草稿', '0', 0, 1, '培训计划发布状态'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d
                  WHERE d.dict_type = 'training_publish_status' AND d.dict_value = '0');

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order, status, remark)
SELECT 'training_publish_status', '已发布', '1', 1, 1, '培训计划发布状态'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d
                  WHERE d.dict_type = 'training_publish_status' AND d.dict_value = '1');

-- 3. 历史数据处理提醒（不自动执行）
--    字段默认值为 '0'，存量计划会变成"草稿"，业务人员将暂时看不到。
--    请在确认演示数据后由管理员逐条发布，禁止批量 UPDATE 直接置为已发布：
--
--    -- 查看存量计划（确认后再决定发布范围）
--    SELECT id, plan_name, status, publish_status, deleted FROM training_plan WHERE deleted = 0;
--
--    -- 仅对确认需要公开的计划逐条发布（示例，把 {id} 替换为实际计划 ID）
--    UPDATE training_plan SET publish_status = '1' WHERE id = {id} AND deleted = 0;

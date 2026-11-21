-- 就业服务流程增量；仅供人工审核后执行，禁止接入应用启动或自动迁移。
-- 适用 MySQL 8.0；执行前备份并停止业务写入，连接时显式选择 disability_db。
-- 执行时核对 employment_service 全表（含逻辑删除）记录数；非空时停止，不自动迁移历史事件。
-- 不修改账号、现有字典或业务记录；历史人员 employment_status 一律保留 NULL。
-- MySQL DDL 隐式提交，不能用事务回滚撤销；脚本内所有预检只读断言，不修改数据。
-- 本文件不使用 --force：任何语句报错都会中断后续执行，不得强行继续。
--
-- 失败重试说明（重要，务必先读）：
--   1) 中断后先判定已完成的阶段，执行以下只读核对，不要凭记忆判断：
--        SELECT COLUMN_NAME FROM information_schema.COLUMNS
--          WHERE TABLE_SCHEMA='disability_db'
--            AND ((TABLE_NAME='employment_service' AND COLUMN_NAME IN ('root_service_id','status'))
--              OR (TABLE_NAME='disabled_person' AND COLUMN_NAME='employment_status'));
--        SELECT INDEX_NAME FROM information_schema.STATISTICS
--          WHERE TABLE_SCHEMA='disability_db' AND TABLE_NAME='employment_service'
--            AND INDEX_NAME LIKE 'idx_employment_service\_%';
--        SELECT dict_type, dict_value, dict_label, status FROM disability_db.sys_dict
--          WHERE dict_type IN ('service_type','employment_service_status','employment_service_result')
--          ORDER BY dict_type, sort_order, id;
--   2) 未完成核对前严禁重跑本文件。DDL 无法自动回退，重跑可能因
--      "字段/索引已存在" 在预检处直接停止，也可能造成重复字典。
--   3) 回滚方式：先恢复本次备份（mysqldump 结果文件），再按差异人工补齐；
--      不要逐条手工 DELETE 字典，也不要重建业务表。
--   4) 存储过程定义在 CALL 之后才 DROP。若 CALL 中途失败，过程会残留，
--      重跑时 CREATE PROCEDURE 会报 already exists；为此已在创建前加入
--      DROP PROCEDURE IF EXISTS，确认无其他迁移正在执行后可直接重跑。
--   5) 执行账号需具备 disability_db 的 SELECT、ALTER、INDEX、INSERT、
--      CREATE ROUTINE、ALTER ROUTINE、EXECUTE 权限。
--
-- 排序规则说明：MySQL 8.0 的 JSON_TABLE 派生列为 utf8mb4_bin，
-- 而表列与 information_schema 列为 utf8mb4_0900_ai_ci（本库实测）。
-- 两者直接比较会触发 ERROR 1267 Illegal mix of collations，
-- 因此所有"派生列 vs 表列"的比较两侧统一加 BINARY，避免依赖环境排序规则。

SET NAMES utf8mb4;
SET @employment_workflow_selected_schema = DATABASE();

-- 只读预检输出，不包含人员资料。
SELECT DATABASE() AS selected_schema, @@version AS mysql_version;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'disability_db'
  AND TABLE_NAME IN ('employment_service', 'disabled_person', 'sys_dict')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'employment_service'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SELECT dict_type, dict_value, dict_label, status
FROM disability_db.sys_dict
WHERE dict_type IN ('service_type', 'employment_status', 'employment_service_status',
                    'employment_service_result')
ORDER BY dict_type, sort_order, id;
SELECT 'employment_service' AS table_name, COUNT(*) AS records_including_deleted FROM disability_db.employment_service
UNION ALL SELECT 'disabled_person', COUNT(*) FROM disability_db.disabled_person
UNION ALL SELECT 'sys_dict', COUNT(*) FROM disability_db.sys_dict;

DELIMITER $$
-- 容错：CALL 中途失败时过程会残留，允许确认后重跑。
DROP PROCEDURE IF EXISTS disability_db.migrate_employment_workflow_20261028$$
CREATE PROCEDURE disability_db.migrate_employment_workflow_20261028()
BEGIN
    DECLARE required_dicts JSON;
    DECLARE added_dicts JSON;

    -- 在过程外捕获所选库，避免过程内 DATABASE() 总返回过程所属库。
    IF COALESCE(@employment_workflow_selected_schema, '') <> 'disability_db' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: explicitly select disability_db before migration';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = 'disability_db'
          AND TABLE_NAME IN ('employment_service', 'disabled_person', 'sys_dict')
          AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB') <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: required InnoDB tables are missing';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'disability_db'
          AND ((TABLE_NAME = 'employment_service' AND COLUMN_NAME IN ('root_service_id', 'status'))
               OR (TABLE_NAME = 'disabled_person' AND COLUMN_NAME = 'employment_status'))) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: workflow columns already exist; inspect partial migration';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'employment_service'
                 AND INDEX_NAME IN ('idx_employment_service_root', 'idx_employment_service_status')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: workflow index already exists';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM JSON_TABLE('[ ["id","bigint"], ["person_id","bigint"], ["job_id","bigint"],
                           ["service_type","varchar(20)"], ["service_date","date"], ["content","text"],
                           ["result","varchar(100)"], ["remark","varchar(500)"], ["deleted","tinyint"],
                           ["create_time","datetime"], ["update_time","datetime"],
                           ["create_by","bigint"], ["update_by","bigint"] ]', '$[*]'
                        COLUMNS (column_name VARCHAR(64) PATH '$[0]', column_type VARCHAR(64) PATH '$[1]')) expected
        LEFT JOIN information_schema.COLUMNS actual
          ON actual.TABLE_SCHEMA = 'disability_db' AND actual.TABLE_NAME = 'employment_service'
         AND BINARY actual.COLUMN_NAME = BINARY expected.column_name
        WHERE actual.COLUMN_NAME IS NULL OR BINARY actual.COLUMN_TYPE <> BINARY expected.column_type
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: employment_service structure differs from reviewed schema';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'disabled_person'
          AND ((COLUMN_NAME = 'id' AND COLUMN_TYPE = 'bigint')
               OR (COLUMN_NAME = 'status' AND COLUMN_TYPE = 'tinyint'))) <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: disabled_person structure differs from reviewed schema';
    END IF;
    IF (SELECT COUNT(*) FROM disability_db.employment_service) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: employment records exist; historical migration needs review';
    END IF;

    SET required_dicts = JSON_ARRAY(
        JSON_ARRAY('service_type', '1', '岗位推荐'),
        JSON_ARRAY('service_type', '2', '企业联系'),
        JSON_ARRAY('service_type', '3', '面试'),
        JSON_ARRAY('service_type', '4', '入职'),
        JSON_ARRAY('service_type', '5', '回访'),
        JSON_ARRAY('employment_status', '0', '待就业'),
        JSON_ARRAY('employment_status', '1', '求职中'),
        JSON_ARRAY('employment_status', '2', '已就业'),
        JSON_ARRAY('employment_status', '3', '离职')
    );
    SET added_dicts = JSON_ARRAY(
        JSON_ARRAY('service_type', '6', '录用'),
        JSON_ARRAY('service_type', '7', '离职'),
        JSON_ARRAY('employment_service_status', '0', '待办'),
        JSON_ARRAY('employment_service_status', '1', '完成'),
        JSON_ARRAY('employment_service_status', '2', '取消'),
        JSON_ARRAY('employment_service_result', '1', '成功'),
        JSON_ARRAY('employment_service_result', '2', '失败'),
        JSON_ARRAY('employment_service_result', '3', '放弃')
    );
    IF EXISTS (
        SELECT 1
        FROM JSON_TABLE(required_dicts, '$[*]' COLUMNS (
            dict_type VARCHAR(50) PATH '$[0]', dict_value VARCHAR(50) PATH '$[1]',
            dict_label VARCHAR(50) PATH '$[2]')) expected
        LEFT JOIN disability_db.sys_dict actual
          ON BINARY actual.dict_type = BINARY expected.dict_type
         AND BINARY actual.dict_value = BINARY expected.dict_value
        WHERE actual.id IS NULL OR COALESCE(actual.status, 0) <> 1
           OR BINARY actual.dict_label <> BINARY expected.dict_label
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: existing employment dictionaries are missing or incompatible';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM JSON_TABLE(added_dicts, '$[*]' COLUMNS (
            dict_type VARCHAR(50) PATH '$[0]', dict_value VARCHAR(50) PATH '$[1]',
            dict_label VARCHAR(50) PATH '$[2]')) expected
        JOIN disability_db.sys_dict actual
          ON BINARY actual.dict_type = BINARY expected.dict_type
         AND BINARY actual.dict_value = BINARY expected.dict_value
        WHERE COALESCE(actual.status, 0) <> 1 OR BINARY actual.dict_label <> BINARY expected.dict_label
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stop: proposed dictionary values conflict with existing values';
    END IF;

    -- 所有业务 DDL 放在预检之后；SIGNAL 会退出整个过程，不能继续执行下方变更。
    ALTER TABLE disability_db.employment_service
        ADD COLUMN root_service_id BIGINT NULL COMMENT '本次应聘首次推荐记录ID；首次推荐为空' AFTER job_id,
        ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT '0'
            COMMENT '办理状态（employment_service_status.dict_value）' AFTER service_type,
        ADD INDEX idx_employment_service_root (root_service_id),
        ADD INDEX idx_employment_service_status (status);
    ALTER TABLE disability_db.disabled_person
        ADD COLUMN employment_status VARCHAR(20) NULL DEFAULT NULL
            COMMENT '就业状态（employment_status.dict_value）；NULL尚未核实' AFTER status;

    INSERT INTO disability_db.sys_dict (dict_type, dict_value, dict_label, sort_order, status)
    SELECT proposed.dict_type, proposed.dict_value, proposed.dict_label, CAST(proposed.dict_value AS UNSIGNED), 1
    FROM JSON_TABLE(added_dicts, '$[*]' COLUMNS (
        dict_type VARCHAR(50) PATH '$[0]', dict_value VARCHAR(50) PATH '$[1]',
        dict_label VARCHAR(50) PATH '$[2]')) proposed
    WHERE NOT EXISTS (
        SELECT 1 FROM disability_db.sys_dict existing
        WHERE BINARY existing.dict_type = BINARY proposed.dict_type
          AND BINARY existing.dict_value = BINARY proposed.dict_value
    );
END$$
CALL disability_db.migrate_employment_workflow_20261028()$$
DROP PROCEDURE disability_db.migrate_employment_workflow_20261028$$
DELIMITER ;

-- 迁移后只读核对；未执行任何业务数据回填。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'disability_db'
  AND ((TABLE_NAME = 'employment_service' AND COLUMN_NAME IN ('root_service_id', 'status'))
       OR (TABLE_NAME = 'disabled_person' AND COLUMN_NAME = 'employment_status'));
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'disability_db' AND TABLE_NAME = 'employment_service'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SELECT 'employment_service' AS table_name, COUNT(*) AS records_including_deleted FROM disability_db.employment_service
UNION ALL SELECT 'disabled_person', COUNT(*) FROM disability_db.disabled_person
UNION ALL SELECT 'sys_dict', COUNT(*) FROM disability_db.sys_dict;
SELECT COUNT(*) AS people_with_verified_employment_status
FROM disability_db.disabled_person WHERE employment_status IS NOT NULL;
SELECT dict_type, dict_value, dict_label, status
FROM disability_db.sys_dict
WHERE dict_type IN ('service_type', 'employment_status', 'employment_service_status',
                    'employment_service_result')
ORDER BY dict_type, sort_order, id;

-- disability_db schema snapshot from local MySQL on 2026-10-12
-- Structure only; no business records.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '企业ID',
  `company_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '企业名称',
  `contact_person` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '详细地址',
  `district` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属区县（关联字典district）',
  `industry` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属行业',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常 0停用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_company_name` (`company_name`),
  KEY `idx_district` (`district`),
  KEY `idx_industry` (`industry`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disabled_cert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '证件ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID（关联disabled_person.id）',
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件编号',
  `cert_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件类型',
  `issue_date` date DEFAULT NULL COMMENT '发证日期',
  `expire_date` date DEFAULT NULL COMMENT '有效期至',
  `issue_org` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发证机构',
  `disability_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '评定时的残疾类别',
  `disability_level` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '评定时的残疾等级',
  `cert_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '证件状态',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`),
  KEY `idx_cert_no` (`cert_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='残疾人证件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disabled_person` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '残疾人ID',
  `name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '姓名',
  `gender` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '性别',
  `id_card` varchar(18) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证号',
  `birth_date` date DEFAULT NULL COMMENT '出生日期',
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `district` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属区县',
  `grid_id` bigint DEFAULT NULL COMMENT '所属网格ID（关联sys_grid.id）',
  `address` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '详细地址',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `disability_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '残疾类别（当前有效值）',
  `disability_level` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '残疾等级（当前有效值）',
  `education` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '学历',
  `status` tinyint DEFAULT '1' COMMENT '档案状态：1正常 0停用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_district` (`district`),
  KEY `idx_disability_type` (`disability_type`),
  KEY `idx_grid_id` (`grid_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='残疾人主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `employment_service` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务记录ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID（关联disabled_person.id）',
  `job_id` bigint DEFAULT NULL COMMENT '岗位ID（关联job.id）',
  `service_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '服务类型（关联字典service_type）',
  `service_date` date DEFAULT NULL COMMENT '服务日期',
  `content` text COLLATE utf8mb4_general_ci COMMENT '服务内容',
  `result` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结果',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_service_type` (`service_type`),
  KEY `idx_service_date` (`service_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='就业服务与跟踪表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `company_id` bigint NOT NULL COMMENT '企业ID（关联company.id）',
  `job_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `salary` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '薪资（字符串，如"3000-5000"或"面议"）',
  `requirement` text COLLATE utf8mb4_general_ci COMMENT '岗位要求（企业原话，含技能关键词）',
  `education_requirement` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '学历要求（关联字典education）',
  `headcount` int DEFAULT '1' COMMENT '招聘人数',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态：0招聘中 1已结束',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_job_name` (`job_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业招聘岗位表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `policy_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '政策ID',
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '政策标题',
  `category` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类（关联字典policy_category）',
  `file_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原始文件名',
  `file_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO文件URL',
  `source` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '政策来源',
  `dify_doc_id` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Dify文档ID',
  `upload_by` bigint DEFAULT NULL COMMENT '上传人',
  `upload_time` datetime DEFAULT NULL COMMENT '上传时间',
  `status` tinyint DEFAULT '1' COMMENT '状态：1有效 0删除',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='政策文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '简历ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID（关联disabled_person.id）',
  `job_intention` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '求职意向（业务人员询问后录入）',
  `expected_salary` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '期望薪资',
  `self_evaluation` text COLLATE utf8mb4_general_ci COMMENT '自我评价',
  `skills_summary` text COLLATE utf8mb4_general_ci COMMENT '技能汇总（从培训记录自动生成）',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='残疾人简历表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subsidy_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '补贴项目ID',
  `item_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '补贴名称',
  `subsidy_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补贴类型（关联字典subsidy_type）',
  `standard` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补贴标准',
  `apply_condition` text COLLATE utf8mb4_general_ci COMMENT '申请条件',
  `apply_material` text COLLATE utf8mb4_general_ci COMMENT '申请材料',
  `source` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '政策来源',
  `status` tinyint DEFAULT '1' COMMENT '状态：1有效 0停用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_item_name` (`item_name`),
  KEY `idx_subsidy_type` (`subsidy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='补贴项目表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subsidy_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID（关联disabled_person.id）',
  `item_id` bigint NOT NULL COMMENT '补贴项目ID（关联subsidy_item.id）',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '实际发放金额',
  `apply_date` date DEFAULT NULL COMMENT '申请日期',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态：0待审核 1已通过 2已发放 3已驳回',
  `approve_date` date DEFAULT NULL COMMENT '审批日期',
  `pay_date` date DEFAULT NULL COMMENT '发放日期',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`),
  KEY `idx_item_id` (`item_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='补贴申请记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型',
  `dict_label` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典标签（显示用）',
  `dict_value` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典值（存储用）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_value` (`dict_type`,`dict_value`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通用字典表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_grid` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '网格ID',
  `grid_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '网格名称',
  `grid_code` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '网格编码',
  `district` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属区县',
  `community` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属社区',
  `grid_worker_id` bigint DEFAULT NULL COMMENT '网格员ID（关联sys_user.id）',
  `grid_worker_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '网格员姓名',
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `status` tinyint DEFAULT '1',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_district` (`district`),
  KEY `idx_worker` (`grid_worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网格表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `operation` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作描述',
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求方法',
  `params` text COLLATE utf8mb4_general_ci COMMENT '请求参数',
  `ip` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP地址',
  `status` tinyint DEFAULT '1' COMMENT '状态：1成功 0失败',
  `error_msg` text COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `cost_time` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父级ID（0为顶级）',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限名称',
  `menu_type` tinyint NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由地址',
  `perms` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识：module:sub:action',
  `icon` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单/权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码：ADMIN/BUSINESS',
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色描述',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`) COMMENT '角色编码唯一'
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色ID（逻辑关联sys_role.id）',
  `menu_id` bigint NOT NULL COMMENT '权限ID（逻辑关联sys_menu.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`) COMMENT '防止重复分配'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码（BCrypt加密）',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：0未删 1已删',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一'
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表（认证）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID（逻辑关联sys_user.id）',
  `role_id` bigint NOT NULL COMMENT '角色ID（逻辑关联sys_role.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`) COMMENT '防止重复分配'
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_demand` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '需求ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID',
  `demand_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '需求类型（关联字典training_type）',
  `demand_desc` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '需求描述',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态：0待处理 1已安排 2已取消',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训需求表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `plan_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '计划名称',
  `category` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '培训大类：1康复类 2就业类',
  `training_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '培训类型（关联字典training_type）',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `location` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '培训地点',
  `capacity` int DEFAULT '0' COMMENT '计划人数',
  `enrolled_count` int DEFAULT '0' COMMENT '已报名人数',
  `organizer` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主办单位',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态：0未开始 1进行中 2已结束',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_training_type` (`training_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `training_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `person_id` bigint NOT NULL COMMENT '残疾人ID',
  `plan_id` bigint NOT NULL COMMENT '培训计划ID',
  `enroll_date` date DEFAULT NULL COMMENT '报名日期',
  `score` decimal(5,2) DEFAULT NULL COMMENT '成绩',
  `result` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结果：优秀/合格/不合格/结业',
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结业证书编号',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_person_id` (`person_id`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


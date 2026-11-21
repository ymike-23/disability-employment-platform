# PROJECT_CONTEXT

> 更新日期：2026-10-13（Asia/Shanghai）。用于新会话接续当前后端项目。
> 依据：当前工作树源码、`pom.xml`、测试配置、最近测试日志，以及本次只读查询的本地 MySQL 表结构和字典数据。
> 本次仅新增本文档，没有修改业务代码、测试代码、配置或数据库。

## 1. 新会话先读

- 工作目录：`D:\毕设\20231103381姚志荣-毕设内容\姚志荣-代码\hd\disability`。
- 这是残疾人管理平台的 Spring Boot 后端，Maven 单模块；当前仓库没有前端实现。
- 已实现：认证、管理员创建业务账号、网格数据权限、残疾人档案、残疾人证件、培训需求、培训计划、培训记录。
- 最近业务开发已完成 `training_record`，不是仍然等待开发的模块。就业、补贴、政策和 AI 的业务接口尚未实现。
- 先执行 `git status --short`。本文档包含未提交工作树的现状，不能只按 Git HEAD 判断模块是否存在，也不要覆盖已有改动。
- 根目录 [CODEBUDDY.md](CODEBUDDY.md) 中“只有脚手架”“没有业务代码”等内容已经过时；以当前源码和实际数据库为准。
- 最新用户任务是生成接续文档。下一步业务开发范围由新的用户指令确定，本文档中的差异说明不构成自动修复任务。

## 2. 技术栈

| 项目 | 当前使用 |
| --- | --- |
| Maven 坐标 | `com.yzr:disability:0.0.1-SNAPSHOT` |
| Java | 编译目标 21；此前本机使用 JDK 25 完成构建 |
| Spring Boot | `4.0.8` |
| Web / 校验 | Spring MVC、Jakarta Validation |
| 安全 | Spring Security、方法级 `@PreAuthorize`、BCrypt |
| 持久化 | MyBatis-Plus `3.5.17`、`BaseMapper`、MySQL JDBC |
| 分页 | `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)` |
| 分页依赖 | 单独引入 `mybatis-plus-jsqlparser:3.5.17` |
| 会话 | Spring Data Redis / Lettuce、JWT `jjwt:0.11.5` |
| JSON | Boot 4 使用 `tools.jackson.databind`；JWT 库另外包含自身 Jackson 依赖 |
| API 文档 | springdoc OpenAPI / Swagger UI `3.1.1` |
| 简化实体 | Lombok，主代码与测试的注解处理器已配置 |
| 测试 | JUnit 5、Spring Boot 集成测试、H2 MySQL 兼容模式 |
| Maven Wrapper | Wrapper `3.3.4`，实际 Maven 发行版 `3.9.16` |

依据：[pom.xml](pom.xml)、[Wrapper 配置](.mvn/wrapper/maven-wrapper.properties)。不要按 Spring Boot 3 的依赖和 Jackson 导入方式替换当前配置。

## 3. 目录与代码风格

```text
src/main/java/com/yzr/disability/
├── DisabilityApplication.java    # 启动类，扫描 mapper 包
├── common/
│   ├── exception/               # BusinessException、GlobalExceptionHandler
│   ├── result/                  # Result<T>、PageResult<T>
│   └── security/                # LoginUser、SecurityUtils
├── config/                      # Security、JWT、MyBatis-Plus、OpenAPI
├── controller/
├── entity/
│   ├── dto/                     # SaveDTO / QueryDTO 等请求对象
│   ├── po/                      # 数据库映射对象
│   └── vo/                      # 接口返回对象
├── mapper/
└── service/
    └── impl/
src/main/resources/application.yml
src/test/java/com/yzr/disability/
src/test/resources/application.yml
src/test/resources/schema-test.sql
sql/
```

- 分层保持 `Controller → Service → Mapper → PO`，不另建架构。
- **实际返回方式**：Service 返回 VO、`PageResult<VO>`、新增 ID 或 `void`；Controller 使用 `Result.success(...)` 包装。不要为了“使用 Result”给现有 Service 重复套壳。
- Mapper 继承 `BaseMapper<PO>`，复杂授权条件或行锁使用现有 Mapper 注解 SQL。
- 实体使用 `@TableName`、自增 `@TableId`；有 `deleted` 列的业务表使用 `@TableLogic`，删除后 `deleted=1`。
- 证件和三个培训模块的写操作使用 `@Transactional(rollbackFor = Exception.class)`。
- 当前实现使用 `BeanUtils.copyProperties` 转换，Service 校验关联、权限和业务规则。新增模块沿用对应模块的注入和命名风格。
- 更新普遍采用非空字段更新；不传或传 `null` 通常保留旧值，不能假设 PUT 会清空全部未传字段。
- 分页参数为 `current`、`size`，默认 `1`、`10`，最大每页 `100`；DTO 校验不合法参数。
- `Result` 字段为 `code / message / data`；`PageResult` 字段为 `records / total / current / size / pages`。

关键公共文件：[Result](src/main/java/com/yzr/disability/common/result/Result.java)、[PageResult](src/main/java/com/yzr/disability/common/result/PageResult.java)、[MyBatis-Plus 配置](src/main/java/com/yzr/disability/config/MybatisPlusConfig.java)。

## 4. 已实现接口

档案与三个培训模块具有相同 CRUD 路由：`GET {base}/page`、`GET {base}/{id}`、`POST {base}`、`PUT {base}/{id}`、`DELETE {base}/{id}`。

| 模块 | base / 接口 | 查询条件及说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login`、`POST /api/auth/logout` | 登录、删除 Redis 登录会话 |
| 账号 | `POST /api/admin/addusers` | 仅 ADMIN；新账号固定 BUSINESS |
| 残疾人档案 | `/api/disabled-person`，上述 CRUD | `name` 模糊、`district`、`disabilityType`、管理员 `gridId` |
| 残疾人证件 | `GET /api/disabled-cert/list` | 必填 `personId`，可筛选 `certNo`、`certStatus`、`isCurrent` |
| 当前证件 | `GET /api/disabled-cert/current?personId=...` | 没有当前证件时 `data=null` |
| 证件维护 | `POST /api/disabled-cert`、`PUT /api/disabled-cert/{id}`、`DELETE /api/disabled-cert/{id}` | 没有独立的证件 `GET /{id}` 接口 |
| 培训需求 | `/api/training-demand`，上述 CRUD | `personId`、`demandType`、`status` |
| 培训计划 | `/api/training-plan`，上述 CRUD | `planName` 模糊、`category`、`trainingType`、`status` |
| 培训记录 | `/api/training-record`，上述 CRUD | `personId`、`planId`、`status` |

业务 Controller 均限制 ADMIN/BUSINESS。分页返回 `Result<PageResult<VO>>`；业务新增返回 `Result<Long>`，修改和删除返回 `Result<Void>`。

Swagger 默认访问地址：`http://localhost:8080/swagger-ui/index.html`；OpenAPI：`http://localhost:8080/v3/api-docs`。当前配置没有覆盖 Boot 默认端口 8080。

## 5. 认证、网格权限与异常码

### 认证

- 请求头使用 `Authorization: Bearer {token}`。
- 登录校验 BCrypt 密码，生成 JWT，并保存到 Redis `login:token:{userId}`，有效期默认 `86400` 秒。
- 请求同时验证 JWT 和 Redis 中的当前 Token；同一账号新登录覆盖旧 Token，退出登录删除该 Redis Key。
- 过滤器重新加载用户和有效角色。实际授权白名单为 `ADMIN`、`BUSINESS`；数据库保留的 `GRID_WORKER` 不参与业务授权。
- 登录和 Swagger/OpenAPI 资源允许匿名访问，其余请求需要认证。

参考：[SecurityConfig](src/main/java/com/yzr/disability/config/SecurityConfig.java)、[JwtAuthenticationFilter](src/main/java/com/yzr/disability/config/JwtAuthenticationFilter.java)、[UserDetailsServiceImpl](src/main/java/com/yzr/disability/service/impl/UserDetailsServiceImpl.java)、[TokenServiceImpl](src/main/java/com/yzr/disability/service/impl/TokenServiceImpl.java)。

### 网格范围

权限入口是 [GridScopeServiceImpl](src/main/java/com/yzr/disability/service/impl/GridScopeServiceImpl.java)：有效网格满足 `sys_grid.grid_worker_id = 当前用户ID`、`status=1`、`deleted=0`。

| 情形 | 当前行为 |
| --- | --- |
| ADMIN | 不受网格限制；`getCurrentGridIds()` 的空列表表示全部 |
| BUSINESS，唯一有效网格 | 档案及人员关联模块按真实 `disabled_person.grid_id` 校验 |
| BUSINESS，无有效网格 | 档案/培训分页返回成功空页；详情和写操作拒绝，业务码 403 |
| BUSINESS，多个有效网格 | 配置异常，拒绝操作，业务码 403 |
| 无有效业务角色 | 拒绝访问，业务码 403 |
| 无登录用户 | 认证失败，业务码 401 |

- 证件、需求、记录的修改/删除先读取数据库中的真实 `person_id`，再校验所属人员网格，不能用请求中的 `personId` 替代授权依据。
- BUSINESS 新增档案时由后端确定 `gridId`；前端传入值不会扩大权限。
- 列表与分页在 SQL 查询及统计总数之前限定权限，不是在查询全部数据后内存过滤。
- 写操作复用 [DisabledPersonMapper](src/main/java/com/yzr/disability/mapper/DisabledPersonMapper.java) 的 `selectAccessibleForUpdate` 锁定有权限的真实人员。
- **计划管理有独立范围**：计划表没有 `grid_id/person_id`，按 `training_plan.create_by → sys_grid.grid_worker_id` 关联当前有效网格；详见下文。

### 异常码不是统一的 HTTP 状态码

- [BusinessException](src/main/java/com/yzr/disability/common/exception/BusinessException.java) 的单参数构造器仍默认 `code=500`。不要全局改成 403，否则会影响已有业务异常。
- 网格缺失、多网格、角色/权限配置异常已经显式使用 `new BusinessException(403, ...)`。
- 参数和字典错误通常用 400；培训记录/需求/计划维护的目标记录不存在时通常 ADMIN 返回 404、BUSINESS 返回 403，报名所关联的计划不存在则返回 404；容量或并发冲突用 409。旧登录失败、重复用户名/身份证号等仍有默认 500 的业务异常。
- [GlobalExceptionHandler](src/main/java/com/yzr/disability/common/exception/GlobalExceptionHandler.java) 返回 `Result.error(...)`，没有统一设置 HTTP 状态，因此 Controller 阶段的错误可能是 **HTTP 200 + `Result.code=403/400/...`**。
- Security 过滤器链的未认证/拒绝访问则明确设置 HTTP 401/403。测试断言需同时理解 HTTP 与响应体业务码，不能混淆。

## 6. 数据模型与模块规则

### 6.1 档案与证件已经拆分

- `disabled_person` 保存基础档案、联系方式、区域/网格、当前残疾类别/等级、档案状态和审计信息。
- `disabled_cert` 保存证号、证件类型、发证/有效期日期、发证机构、评定时残疾类别/等级、证件状态、当前标记等。
- 关系：`disabled_cert.person_id = disabled_person.id`；一个人员可以有多条历史证件。
- `DisabledPersonPO / DisabledPersonSaveDTO / DisabledPersonVO` 已移除旧的 `certNo / certIssueDate / certExpireDate / certIssueOrg / certStatus`。实际 DTO 名称是 `DisabledPersonSaveDTO`。
- `/api/disabled-person` 保留，只处理人员 CRUD。当前人员 VO 包含组合查询的 `gridName`，没有组合当前证件；以后需要展示证件时在 Service 组合，不能把证件持久化字段放回人员 PO。
- `is_current` 实际为 `TINYINT(1) NOT NULL DEFAULT 1`；索引为 `idx_person_id`、`idx_cert_no`、`idx_person_current(person_id,is_current)`。
- 新增/修改设置 `isCurrent=1` 时，在同一事务内先把该人员旧当前证件设为 0。人员行锁防止同一人员并发产生两条当前证件。
- 删除当前证件不会自动提升历史证件。多条未删除当前证件被视为数据冲突，返回 409。
- 证件归属人员不可修改；有效期结束日期必须晚于发证日期。
- 查询 DTO 虽保留 `expiringSoon / expireWithinDays`，当前 Service 的列表查询没有执行这两个条件；不能据此认定已完成到期筛选或提醒流程。

参考：[DisabledPersonServiceImpl](src/main/java/com/yzr/disability/service/impl/DisabledPersonServiceImpl.java)、[DisabledCertServiceImpl](src/main/java/com/yzr/disability/service/impl/DisabledCertServiceImpl.java)。

### 6.2 培训需求 `training_demand`

实际列：`id, person_id, demand_type, demand_desc, status, remark, deleted, create_time, update_time, create_by, update_by`。

- 新增必须关联存在且可访问的人员；默认状态字符串 `"0"`（待处理）。
- 需求状态当前仍按既有 `"0" / "1" / "2"`（待处理/已安排/已取消）校验，不能套用培训计划的 `training_status` 值含义。
- 修改必须保持 `personId` 不变，删除为逻辑删除。
- `demandType` 当前只有长度/文本规范化校验，**尚未通过 SysDictMapper 校验**，虽然表注释关联 `training_type`。
- 没有实现需求自动转计划、需求与计划的直接关联字段或安排流程。

参考：[TrainingDemandServiceImpl](src/main/java/com/yzr/disability/service/impl/TrainingDemandServiceImpl.java)。

### 6.3 培训计划 `training_plan`

实际列：`id, plan_name, category, training_type, start_date, end_date, location, capacity, enrolled_count, organizer, status, remark, deleted, create_time, update_time, create_by, update_by`。

- 新增要求计划名称、开始/结束日期、容量；默认 `enrolledCount=0`、状态字符串 `"0"`。
- 校验 `startDate <= endDate`、`capacity > 0`、`0 <= enrolledCount <= capacity`。
- 修改容量不能小于数据库当前 `enrolled_count`；即使请求同时调低人数，也不能绕过该约束。
- `category / trainingType / status` 都使用 String 字典值，并校验对应启用的 `sys_dict` 项；VO 返回原始值，不在 Service 写中文名称映射。
- **当前权限关联**：BUSINESS 的唯一有效网格必须满足 `g.grid_worker_id = training_plan.create_by`。`createBy` 来自当前登录用户，前端不能指定。
- 这不是计划表中永久保存的网格归属；网格负责人/绑定变化会影响计划的可见范围。ADMIN 可维护全部，BUSINESS 不能据此管理其他创建人的计划或无创建人的历史计划。
- 写入时 [TrainingPlanMapper](src/main/java/com/yzr/disability/mapper/TrainingPlanMapper.java) 使用权限条件及 `FOR UPDATE`，容量校验读取锁定后的当前人数。
- 计划保存接口仍允许按既有校验直接传 `enrolledCount`，不能假设该列完全由培训记录数量自动重建。

参考：[TrainingPlanServiceImpl](src/main/java/com/yzr/disability/service/impl/TrainingPlanServiceImpl.java)。

### 6.4 培训记录 `training_record`：注意物理列名

实际 MySQL 列：`id, person_id, plan_id, enroll_date, score, result, cert_no, remark, deleted, create_time, update_time`。

| Java / API 字段 | 实际数据库列 | 说明 |
| --- | --- | --- |
| `personId` | `person_id` | 关联人员；授权依据 |
| `planId` | `plan_id` | 关联培训计划 |
| `status` | **`result`** | `@TableField("result")`，存 `training_record_status.dict_value` |
| `certificateNo` | **`cert_no`** | `@TableField("cert_no")`，培训结业证书编号 |
| `enrollDate` | `enroll_date` | 新增不传默认当天 |
| `score` | `score DECIMAL(5,2)` | Java `BigDecimal` |

**数据库没有 `status`、`certificate_no`、`create_by`、`update_by` 列。** 当前通过 PO 映射适配既有结构，没有增加列或执行历史数据迁移。培训结业证书编号与残疾人证号属于不同领域。

- 状态默认 `"1"`；新增接受当前有效报名状态 `"1"～"4"`，拒绝直接新增 `"5"`（已取消）。状态值还必须是启用字典项。
- 新增校验真实人员权限、计划存在且未删除、人数小于容量；在同一事务内插入记录并使计划人数 +1。
- 修改先校验真实记录归属；`personId / planId` 必填且不可变。不传或传 null 的其他字段保留原值。
- 当前有效报名定义为状态 `"1" / "2" / "3" / "4"`，均占用名额；改成 `"5"` 减人数，从 `"5"` 恢复有效状态时重新校验容量并加人数。
- 删除为逻辑删除，仅有效报名释放人数；已取消记录删除不再扣人数。
- 人员、记录和计划行锁配合条件更新，防止并发超额、同一记录重复扣数及负人数；计数失败抛异常，整个事务回滚。
- ADMIN 可维护所属人员已删除/缺失的历史记录；BUSINESS 仍要求人员存在且属于本网格。已删除计划的有效历史报名可以释放人数，但不能新增或恢复报名。
- **报名资格按人员网格授权，不复用计划维护接口的创建人限制**：BUSINESS 可为本格人员报名任意存在且未删除、有容量的计划；不会因此获得该计划的 CRUD 权限。
- 当前未增加同一人员同一计划的唯一报名约束，也未增加计划日期/状态限制报名的规则；成绩只校验数值精度，没有 0～100 范围规则。

参考：[TrainingRecordPO](src/main/java/com/yzr/disability/entity/po/TrainingRecordPO.java)、[TrainingRecordServiceImpl](src/main/java/com/yzr/disability/service/impl/TrainingRecordServiceImpl.java)、[TrainingRecordMapper](src/main/java/com/yzr/disability/mapper/TrainingRecordMapper.java)、[TrainingRecordSaveDTO](src/main/java/com/yzr/disability/entity/dto/TrainingRecordSaveDTO.java)。

## 7. 字典规范与已核对值

`sys_dict` 使用 `dict_type / dict_value / dict_label / status`，唯一键为 `(dict_type, dict_value)`；**没有 `deleted` 列**，`SysDictPO` 不使用 `@TableLogic`。

下列为 2026-10-13 本地 MySQL 只读查询结果，所列项均为启用状态；以后可能调整，应再次读取数据库，不能把本文档当永久枚举白名单。

| dict_type | dict_value 与展示含义 |
| --- | --- |
| `training_category` | `1` 康复类；`2` 就业类 |
| `training_status` | `0` 未开始；`1` 报名中；`2` 进行中；`3` 已结束；`4` 已取消 |
| `training_record_status` | `1` 已报名；`2` 培训中；`3` 已完成；`4` 未通过；`5` 已取消 |
| `training_type` | `1～6` 各类康复训练；`11` 按摩；`12` 电商；`13` 手工；`14` 计算机；`15` 家政；`99` 其他 |
| `employment_status` | `0` 待就业；`1` 求职中；`2` 已就业；`3` 离职 |
| `service_type` | `1` 岗位推荐；`2` 企业联系；`3` 面试；`4` 入职；`5` 回访 |
| `subsidy_type` | `1` 生活；`2` 护理；`3` 就业；`4` 教育；`5` 辅助器具；`9` 其他补贴 |
| `policy_category` | `1` 康复；`2` 就业；`3` 补贴；`4` 维权；`9` 其他政策 |

继续开发的约定：

1. status/type/category 等字段先检查 `sys_dict`；数据库存 `dict_value`，Java 类型与实际列一致，不存中文名称。
2. 合法性应校验对应 `dict_type`、精确 `dict_value` 和 `status=1`，中文标签及停用值不能当合法入参。
3. 展示中文使用统一字典机制；当前只实现 [SysDictPO](src/main/java/com/yzr/disability/entity/po/SysDictPO.java) / [SysDictMapper](src/main/java/com/yzr/disability/mapper/SysDictMapper.java) 和培训服务读取校验，没有独立字典 API，也没有统一展示名称转换。展示部分后续统一处理。
4. 新状态先确认是否需要扩展字典，并检查业务语义，尤其培训记录的有效报名状态集合和人数联动；不能仅增加字典项就假定流程自动支持。

现状中的历史例外需明确区分：证件服务仍使用 `正常/换证中/注销/挂失` 中文状态，默认正常，而数据库 `cert_status` 默认值为 `"0"`；需求类型也尚未做字典校验。它们不是新模块应照搬的枚举规范，本次没有改造这些模块。

## 8. 数据库和 SQL 文件的边界

- 正常运行连接 `localhost:3306/disability_db`，Redis 为 `localhost:6379`，配置在 [主 application.yml](src/main/resources/application.yml)。密码、JWT 密钥等从本机配置读取，不复制到接续文档或日志。
- [disability_db_schema_snapshot.sql](sql/disability_db_schema_snapshot.sql) 是 **2026-10-12 的结构快照**，不包含字典数据，不能直接认定为当前最新 DDL。
- 本次核对发现该快照的 `disabled_cert` 缺少实际已有的 `is_current` 和 `idx_person_current`；计划状态注释也仍写旧的 0/1/2 含义。以实际表字段及启用字典为准。
- `training_record.result` 的旧注释是成绩结果（优秀/合格等），当前代码用它存培训记录状态值。旧中文/空值数据没有进行迁移，不应假设历史记录已全部符合新状态语义。
- [alter_disabled_person_add_cert_fields.sql](sql/alter_disabled_person_add_cert_fields.sql) 是旧证件字段扩展脚本，与当前领域拆分冲突，不要自动执行或据此把 cert 字段加回人员表。
- [schema-test.sql](src/test/resources/schema-test.sql) 只初始化 H2 测试库，包含证件当前标记与索引、培训表及测试字典种子。H2 索引名按 schema 唯一，部分培训索引带表名前缀，这是与 MySQL 每表索引命名的适配。
- 人员删除当前是逻辑删除，不代表已实现所有关联业务级联删除；不同模块对历史关联数据的可见性存在差异，应读取相应 Service。

## 9. 运行与测试

在项目根目录执行 Windows PowerShell 命令：

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test-compile
.\mvnw.cmd test
.\mvnw.cmd '-Dtest=TrainingRecordTest' test
```

正常应用启动需要 MySQL 和 Redis；数据库表须已准备，当前没有自动业务迁移工具。

### 测试环境

- [测试 application.yml](src/test/resources/application.yml) 使用 `jdbc:h2:mem:disability_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`，自动执行测试 schema，不连接业务 MySQL。
- HTTP 集成测试通过登录接口获得 JWT，覆盖角色、网格、CRUD、逻辑删除和异常响应；测试 JSON 使用 `tools.jackson.databind.ObjectMapper`。
- **Redis 是真实依赖，没有被 H2 替代或统一 Mock**，需本地 6379 可用。此前临时测试 Redis 已在测试后关闭；再次运行前确认服务已启动。
- 本机 Redis 程序位于 `D:\Redis\Redis-5.0\redis-server.exe`。运行辅助服务时只操作自己启动的进程，不关闭用户已有服务。

### 最近一次完整测试证据

2026-10-13 10:03（+08:00）的 `target/training-record-test-run.log` 显示 **BUILD SUCCESS，110 tests，0 failures，0 errors，0 skipped**。

| 测试类 | 数量 |
| --- | ---: |
| `AdminCreateUserTest` | 7 |
| `AuthTest` | 5 |
| `DisabilityApplicationTests` | 1 |
| `DisabledCertTest` | 12 |
| `DisabledPersonPermissionTest` | 13 |
| `ExceptionHandlingTest` | 5 |
| `PagePluginTest` | 2 |
| `RedisSessionTest` | 4 |
| `TrainingDemandTest` | 15 |
| `TrainingPlanTest` | 21 |
| `TrainingRecordTest` | 25 |
| 合计 | **110** |

`TrainingRecordTest` 除基本功能外，还覆盖取消/恢复人数、容量恢复失败、并发争抢名额、并发删除、插入失败回滚、人数异常回滚和历史关联数据处理。

`target/surefire-reports` 可能残留已经删除的 `DisabledPersonCertTest` 旧报告；不能把目录内所有历史报告相加当成本轮测试数量。当前源码测试已经迁移为 `DisabledCertTest`。

本次只生成文档，没有重新运行测试；以上是已读取日志和报告的历史验证结果。`target` 下日志、报告和临时设置不属于持久的源码交付物，clean 后可能不存在。

### 本机 Maven 镜像问题的备用运行方式

此前系统 Maven 设置中的阿里云镜像不可用，已使用本地缓存和空 settings 跑通。缓存存在时可用：

```powershell
$env:MAVEN_USER_HOME = 'C:/Users/21025/.m2'
.\mvnw.cmd -o `
  -s target/cert-build-settings.xml `
  -gs target/cert-build-settings.xml `
  '-Dmaven.repo.local=C:/Users/21025/.m2/repository' test
```

`target/cert-build-settings.xml` 是本机临时文件，内容为 `<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"></settings>`，不是正式项目配置。若已被 clean 删除，需要先恢复该临时文件；离线模式仅在依赖已缓存时可用。镜像或 Redis 不可用应先按环境问题排查，不能据此修改业务逻辑。

## 10. 未提交状态和继续开发约定

生成本文档前，分支为 `main`，HEAD 为 `42b5931`；工作树已有以下未提交内容：

- 培训计划 DTO/PO/VO/Controller/Service 及测试的字典适配修改。
- 新增 `SysDictPO`、`SysDictMapper`。
- 新增完整 `TrainingRecord` 的 PO、SaveDTO、QueryDTO、VO、Mapper、Service、ServiceImpl、Controller 和 `TrainingRecordTest`。
- `src/test/resources/schema-test.sql` 的相关同步修改。

这些是开始本次文档任务时已存在的改动。本文档只是记录它们，不代表已经提交或允许回滚。

后续开发保持以下约定：

- 只实现当次用户指定的模块；不要顺带处理其他 P1/P2、换证流程、到期提醒、就业、补贴或 AI 功能。
- 沿用现有 API、分层、权限体系、MyBatis-Plus、Result/PageResult 和异常处理方式。
- 除非用户新授权，不修改生产 DDL、不增加数据库字段、不引入新框架或依赖，不改动当前任务之外的既有模块。
- 授权必须依据数据库真实人员/创建人关系；不能信任前端提供的人员、网格或创建人来扩大范围。
- 保留报名记录与人数的事务、行锁和条件更新约束；修改相关行为时运行有意义的并发/回滚测试。
- 业务修改完成后按用户要求运行 Maven 测试，报告实际文件改动、接口、测试结果和已有模块影响；环境失败与业务失败分开说明。
- 需要进一步了解某条规则时直接读取本文链接的源码及实际表/字典，不从过时文档重新设计架构。

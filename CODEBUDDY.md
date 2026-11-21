# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 当前执行规则（独立完整）

以下为本项目当前有效的核心开发约束，本文件直接给出执行所需规则。Ponytail 仅做项目级轻量规则整合，不授权安装插件或修改 Codex、CC Switch、CodeBuddy 全局配置。

- **先确认范围，再做最小完整修改。** 读相关实现，检查调用方、测试、测试数据、配置和接口契约，以及可能的数据丢失、泄露或兼容影响。修复前搜索相关调用，在共享根因处修复，并完成本次变更必需的关联调整；保留用户已有改动。
- **优先复用。** 依次使用项目已有实现、标准库或平台能力、已安装依赖；保持可读性与边界正确性，不擅自增加抽象、包装、选项、依赖、字段、模块或预留功能。移动或合并代码时保留校验和错误处理。
- **遵守授权范围。** 用户明确确认的业务规则优先于通用精简偏好；只读分析不修改文件，不执行会生成构建产物、日志或改变项目状态的命令。未经明确授权，不执行生产数据库 DDL、不修改真实业务数据，不删除重要文件或执行不可逆操作。
- **沿用现有架构。** Java 21、Spring Boot、MyBatis-Plus、Spring Security、JWT、Redis、MySQL；保持 Controller → Service → Mapper → PO、DTO/VO、Service 接口及 BaseMapper。Controller 使用 `Result<T>`，分页使用 `PageResult`；Service 返回类型遵循相邻实现，不重复包装或改动既有契约。
- **沿用异常机制。** 使用 `BusinessException` 和全局异常处理；权限业务异常显式 code=403，不修改默认业务异常码或其他业务异常，也不擅自改变业务 code 与 HTTP 状态的处理方式。
- **保留认证与数据权限。** 沿用各模块 RBAC、JWT/Redis、网格授权，不能把单个模块规则推广到所有模块。人员关联业务必须按数据库真实记录及 person_id → disabled_person.grid_id 校验；禁止相信前端 personId 或归属字段，列表、详情、写入均遵守对应模块规则。
- **字典存值。** status、type、category 等先查 `sys_dict`，已有字典存 `dict_value`，Java 类型与其匹配；不存中文名称，不在 Service 写中文状态或展示映射。展示复用字典机制，缺少时说明后续统一处理；新增值先确认字典是否需扩展，历史数据迁移另行授权。
- **逻辑删除与一致性不能省。** 对已有 `deleted` 字段的业务表保持 `@TableLogic` 和现有逻辑删除，不物理删除；不向无此字段的表（如 `sys_dict`）强加字段或注解。保留必要的 `@Transactional(rollbackFor = Exception.class)`、行锁、带条件更新、防重复与唯一约束；关联记录和计数在同一事务中维护。不得因精简削减权限、校验、错误处理、事务、并发控制或必要测试。
- **验证与风险如实报告。** 非平凡逻辑保留或补充必要测试，按影响检查调用方、测试数据、配置及权限、回滚、并发场景；运行针对性测试和项目要求的检查。纯文档或低影响变更按影响验证，不为形式新增测试。未执行、失败、环境阻塞分别说明，不得把未运行的测试报告为通过。回复末尾用一至两行说明未做或未验证的内容及实际风险。
- **保持现有执行规范。** `.codebuddy/rules/_毕设项目开发执行规范.mdc` 的有效规则继续保留；对明确授权的常规可逆开发直接推进，验证并如实汇报。该默认执行原则不能覆盖用户只读要求或授权边界。

## 下方原文的适用边界

以下从“仓库现状（先读这段）”起保留早期记录。关于“只有脚手架/单个测试、没有 Git/持久层/数据源、默认 HTTP Basic、推荐引入 JPA、按前端模拟数据定义实体与契约”的说明已经过时，不作为当前事实或实施依据；目录副本、前端现状等描述也须先核实，旧文中的删除建议不构成删除授权。

下方 Maven 命令、包路径及工程配置等仍有效的内容，在核对当前 `pom.xml`、配置和实际代码后沿用；不根据旧文重建设计或改回早期状态。`PROJECT_CONTEXT.md` 同样属于带时效的导航，使用前核对当前实现及数据库设计；仓库存在迁移 SQL 不代表生产库已执行。

## 仓库现状（先读这段）

- 当前工作区 `hd/disability` 是**呼和浩特残疾人管理大数据智能平台的后端**，目前只有 Spring Initializr 生成的脚手架：**没有任何业务代码**（仅 1 个启动类 + 1 个冒烟测试）。
- **存在重复目录**：`disability/` 是外层项目的**完整副本**（pom、源码、mvnw 全部一致，应是 zip 解压两次导致的）。**只在根目录这一层改代码**，不要改 `disability/` 里的副本；确认无用后可删除它。它不是一个 Maven module，外层构建不会编译它。
- 没有 Git 仓库、没有 lint/CI 配置、没有 README（根目录 `HELP.md` 只是 Initializr 生成的模板文档）。
- 上一级 `../CODEBUDDY.md` 描述的是前端项目，且其"hd/ 是空目录"的说法已过时，不要照抄。

## 常用命令

在 `d:/毕设/20231103381姚志荣-毕设内容/姚志荣-代码/hd/disability` 下执行（Windows 用 `mvnw.cmd`）：

- `.\mvnw.cmd spring-boot:run`：启动应用，默认端口 8080。首次执行 Maven Wrapper 会先下载 Maven 3.9.16（`distributionType=only-script`），需联网；也可直接用系统已装的 `mvn`（本机 `D:/maven-1/maven`）和 JDK 25。
- `.\mvnw.cmd test`：运行全部测试（当前只有 `DisabilityApplicationTests#contextLoads`）。
- `.\mvnw.cmd "-Dtest=DisabilityApplicationTests" test`：只跑指定的单个测试类；加 `#方法名` 可精确到方法。
- `.\mvnw.cmd clean package`：清理并打包成可执行 jar，产物在 `target/`。`-DskipTests` 可跳过测试。
- `java -jar target\disability-0.0.1-SNAPSHOT.jar`：运行打包后的 jar。
- **没有 lint 命令**：项目未配置 Checkstyle/Spotless/PMD，不要臆造 `mvn lint`。

## 架构概览

### 技术栈与工程配置

Spring Boot **4.0.8**（注意是 4.x，不是常见的 3.x）Maven 单模块工程，`groupId=com.yzr`、`artifactId=disability`、`version=0.0.1-SNAPSHOT`，`pom.xml` 里 `<java.version>21</java.version>`（本机装的是 JDK 25，能正常编译 21 目标）。

Spring Boot 4 的 starter 命名与 3.x 不同，写依赖时别用旧名：

| 用途 | 本项目已用的 artifactId |
| --- | --- |
| Web MVC | `spring-boot-starter-webmvc`（3.x 叫 `-web`） |
| Web 测试 | `spring-boot-starter-webmvc-test`（3.x 叫 `-web-test`） |
| 安全 | `spring-boot-starter-security` + `spring-boot-starter-security-test` |

已引入但**尚未启用**的：`org.mariadb.jdbc:mariadb-java-client`（`runtime` 作用域，只有驱动、没有连接池/ORM）和 Lombok（`optional`，已在 `maven-compiler-plugin` 里配好 `annotationProcessorPaths`，直接写 `@Data` 等注解即可生效）。

`src/main/resources/application.properties` 只有一行 `spring.application.name=disability`——**没有 `spring.datasource.*` 配置**。

### 现有代码

`src/main/java/com/yzr/disability/DisabilityApplication.java`：`@SpringBootApplication` + `SpringApplication.run`，无额外配置。所有新代码都放在 `com.yzr.disability` 包及其子包下，靠组件扫描自动注册。

`src/test/java/com/yzr/disability/DisabilityApplicationTests.java`：`@SpringBootTest` 的 `contextLoads()` 空测试，用来验证 Spring 容器能起来。**新增任何 Bean/配置后这个测试会立刻失败**，是唯一的自动化校验手段。

### 落地业务代码时必须先解决的三个坑

1. **没有持久层框架**：只有 JDBC 驱动，没有 `spring-boot-starter-data-jpa`、MyBatis 或 `spring-boot-starter-jdbc`。要建表/查库必须先补一个依赖（推荐 JPA，与现有 MariaDB 驱动配套），并配 `spring.datasource.url/username/password`。
2. **Security 处于默认全鉴权状态**：`spring-boot-starter-security` 在 classpath 上且无任何配置，所有请求默认需要 HTTP Basic 认证，用户名 `user`、随机密码打印在启动日志里。写接口联调前要么加 `SecurityFilterChain` 配置，要么先规划好登录/鉴权方案。
3. **跨域未配置**：前端是独立工程，联调时需要在后端加 CORS 配置或让前端走 Vite 代理。

### 关联的前端与领域模型

前端在同级的 `../../qd/care-data-vue`（Vue 3.5 + Vite + Pinia + vue-router 脚手架，详细约定见 `../CODEBUDDY.md`）。它是本次后端要对接的对象，目前**只有硬编码模拟数据、没有任何接口调用**。

真正定义了业务字段的是前端 `src/view/档案地图.html` 里的全局常量 `disabledData`（12 条模拟档案），这是设计实体类和接口契约的依据：

- `id`（1501xx 编号）、`name`、`gender`、`age`
- `type`（残疾类别：肢体 / 视力 / 听力 / 智力 / 精神 / 多重）
- `level`（一 / 二 / 三级）
- `district`（新城区、回民区、玉泉区、赛罕区、土左旗、托克托县、和林格尔县）
- `address`、`lat`、`lng`
- `status`（正常 / 换证中）
- `subsidy`（困难生活补贴 / 重度护理补贴 / 两项补贴 / 无）

前端另外两个原型 `src/view/code.html`（数据大屏，统计卡片 + 手写 DOM/CSS 图表，没有用 ECharts）和 `src/view/模型.html`（问答界面，答案由 `getMockResponse()` 关键词匹配写死，注释标明"可替换为真实API"）也对应着后端的统计聚合接口和问答接口。

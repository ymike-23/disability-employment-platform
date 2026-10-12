# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

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

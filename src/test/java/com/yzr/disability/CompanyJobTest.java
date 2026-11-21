package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.entity.dto.CompanyQueryDTO;
import com.yzr.disability.entity.dto.CompanySaveDTO;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.dto.JobSaveDTO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.mapper.CompanyMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.service.CompanyService;
import com.yzr.disability.service.JobService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** 企业、岗位真实 HTTP 与事务集成测试；所有测试写入仅发生在 H2 内存库。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanyJobTest {

    private static final String COMPANY = "/api/company";
    private static final String JOB = "/api/job";
    private static final String PASSWORD = "Test@123456";

    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private CompanyService companyService;
    @Autowired private JobService jobService;
    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysUserRoleMapper userRoleMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();
    private final String run = Long.toString(System.nanoTime(), 36);
    private Long adminId;
    private Long businessId;
    private String adminToken;
    private String businessToken;
    private String workerToken;
    private int caseNumber;
    private String prefix;
    private Long companyA;
    private Long companyB;

    @BeforeAll
    void users() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:"));
        }
        adminId = createUser("cj_admin_" + run, "ADMIN");
        businessId = createUser("cj_business_" + run, "BUSINESS");
        createUser("cj_worker_" + run, "GRID_WORKER");
        adminToken = login("cj_admin_" + run);
        businessToken = login("cj_business_" + run);
        workerToken = login("cj_worker_" + run);
    }

    @BeforeEach
    void fixtures() {
        prefix = "企业岗位" + run + "-" + (++caseNumber) + "-";
        companyA = seedCompany("A企业", "1", "制造业", 1);
        companyB = seedCompany("B企业", "2", "服务业", 0);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    void tokens() {
        userIds.forEach(id -> redis.delete("login:token:" + id));
    }

    @Test
    @DisplayName("企业新增默认启用，服务端审计拒绝前端伪造字段")
    void createCompanyDefaultsAndServerAudit() throws Exception {
        Map<String, Object> body = companyBody();
        body.put("companyName", "  " + prefix + "新增企业  ");
        spoofAudit(body);
        Long id = createdId(COMPANY, body);
        assertEquals(prefix + "新增企业", text("company", "company_name", id));
        assertEquals(1, number("company", "status", id));
        assertEquals(0, number("company", "deleted", id));
        assertServerAudit("company", id);
    }

    @Test
    @DisplayName("企业详情和列表返回完整业务字段，使用独立VO隐藏内部字段")
    void companyQueriesReturnCompleteBusinessInformation() throws Exception {
        JsonNode detail = data("GET", COMPANY + "/" + companyA, null, businessToken);
        assertCompanyFields(detail, companyA);
        JsonNode page = page(COMPANY, "companyName=" + enc(prefix), businessToken);
        assertEquals(2, page.get("total").asLong());
        assertCompanyFields(findRecord(page, companyA), companyA);
        assertEquals(0, findRecord(page, companyB).get("status").asInt());
    }

    @Test
    @DisplayName("企业修改保留未传与null可选字段，创建人及创建时间不可伪造")
    void updateCompanyPreservesOptionalNullsAndCreationAudit() throws Exception {
        Timestamp created = jdbc.queryForObject("SELECT create_time FROM company WHERE id=?", Timestamp.class, companyA);
        Map<String, Object> body = companyBody();
        body.put("companyName", prefix + "修改企业");
        for (String field : List.of("contactPerson", "contactPhone", "address", "district", "industry", "remark", "status")) {
            body.put(field, null);
        }
        spoofAudit(body);
        assertCode(200, request("PUT", COMPANY + "/" + companyA, body, adminToken));
        JsonNode detail = data("GET", COMPANY + "/" + companyA, null, adminToken);
        assertEquals(prefix + "修改企业", detail.get("companyName").asText());
        assertEquals("测试联系人", detail.get("contactPerson").asText());
        assertEquals("13800000000", detail.get("contactPhone").asText());
        assertEquals("测试地址", detail.get("address").asText());
        assertEquals("1", detail.get("district").asText());
        assertEquals("制造业", detail.get("industry").asText());
        assertEquals("企业备注", detail.get("remark").asText());
        assertEquals(1, detail.get("status").asInt());
        assertEquals(-1, number("company", "create_by", companyA));
        assertEquals(adminId.longValue(), number("company", "update_by", companyA));
        assertEquals(created, jdbc.queryForObject("SELECT create_time FROM company WHERE id=?", Timestamp.class, companyA));
        assertCode(200, request("PUT", COMPANY + "/" + companyA, Map.of("companyName", prefix + "只修改名称"), adminToken));
        assertEquals("企业备注", text("company", "remark", companyA));
    }

    @Test
    @DisplayName("企业删除使用逻辑删除，删除后查询修改再次删除均404")
    void companyLogicalDeleteAndMissingRecords() throws Exception {
        assertCode(200, request("DELETE", COMPANY + "/" + companyA, null, adminToken));
        assertEquals(1, number("company", "deleted", companyA));
        assertEquals(adminId.longValue(), number("company", "update_by", companyA));
        assertCode(404, request("GET", COMPANY + "/" + companyA, null, adminToken));
        assertCode(404, request("PUT", COMPANY + "/" + companyA, companyBody(), adminToken));
        assertCode(404, request("DELETE", COMPANY + "/" + companyA, null, adminToken));
        assertEquals(1, page(COMPANY, "companyName=" + enc(prefix), adminToken).get("total").asInt());
        assertCode(404, request("GET", COMPANY + "/9223372036854775807", null, businessToken));
    }

    @Test
    @DisplayName("企业分页支持各筛选及组合条件，包含停用企业")
    void companyPageFilters() throws Exception {
        String base = "companyName=" + enc(prefix);
        assertEquals(List.of(companyA), ids(page(COMPANY, base + "&district=1", adminToken)));
        assertEquals(List.of(companyB), ids(page(COMPANY, base + "&industry=" + enc("服务"), adminToken)));
        assertEquals(List.of(companyB), ids(page(COMPANY, base + "&status=0", businessToken)));
        assertEquals(List.of(companyA), ids(page(COMPANY,
                base + "&district=1&industry=" + enc("制造") + "&status=1", businessToken)));
        assertEquals(0, page(COMPANY, base + "&district=2&status=1", adminToken).get("total").asLong());
    }

    @Test
    @DisplayName("无筛选查询全部未删除企业，分页默认10最大100且相同时间排序稳定")
    void companyUnfilteredPageAndStablePagination() throws Exception {
        jdbc.update("UPDATE company SET create_time=? WHERE id IN (?,?)", Timestamp.valueOf("2025-01-01 00:00:00"), companyA, companyB);
        JsonNode unfiltered = page(COMPANY, "", businessToken);
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE deleted=0", Long.class).longValue(), unfiltered.get("total").asLong());
        assertEquals(10, unfiltered.get("size").asLong());
        String filter = "companyName=" + enc(prefix);
        assertEquals(List.of(companyB, companyA), ids(page(COMPANY, filter + "&size=100", adminToken)));
        assertEquals(List.of(companyB), ids(page(COMPANY, filter + "&size=1&current=1", adminToken)));
        assertEquals(List.of(companyA), ids(page(COMPANY, filter + "&size=1&current=2", adminToken)));
        assertEquals(List.of(companyA), ids(page(COMPANY, filter + "&size=1&current=2", adminToken)));
        assertCode(400, request("GET", COMPANY + "/page?size=101", null, adminToken));
        assertCode(400, request("GET", COMPANY + "/page?current=0", null, adminToken));
        assertCode(400, request("GET", COMPANY + "/page?size=0", null, adminToken));
    }

    @Test
    @DisplayName("BUSINESS无需网格即可读取公共企业岗位，但不能写入")
    void businessReadsPublicResourcesButCannotWrite() throws Exception {
        Long job = seedJob(companyA, "公共岗位", "1", "1");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_grid WHERE grid_worker_id=? AND deleted=0", Integer.class, businessId));
        for (String api : List.of(COMPANY, JOB)) {
            Long id = api.equals(COMPANY) ? companyA : job;
            Map<String, Object> body = api.equals(COMPANY) ? companyBody() : jobBody(companyA);
            assertCode(200, request("GET", api + "/page", null, businessToken));
            assertCode(200, request("GET", api + "/" + id, null, businessToken));
            assertCode(403, request("POST", api, body, businessToken));
            assertCode(403, request("PUT", api + "/" + id, body, businessToken));
            assertCode(403, request("DELETE", api + "/" + id, null, businessToken));
        }
        assertEquals(0, number("company", "deleted", companyA));
        assertEquals(0, number("job", "deleted", job));
    }

    @Test
    @DisplayName("其他角色和匿名用户不能访问企业或岗位的任何接口")
    void otherRolesAndAnonymousAreDenied() throws Exception {
        Long job = seedJob(companyA, "角色测试", "1", "0");
        for (String api : List.of(COMPANY, JOB)) {
            Long id = api.equals(COMPANY) ? companyA : job;
            Map<String, Object> body = api.equals(COMPANY) ? companyBody() : jobBody(companyA);
            for (String token : List.of(workerToken, "")) {
                int expected = token.isEmpty() ? 401 : 403;
                String authorization = token.isEmpty() ? null : token;
                assertCode(expected, request("GET", api + "/page", null, authorization));
                assertCode(expected, request("GET", api + "/" + id, null, authorization));
                assertCode(expected, request("POST", api, body, authorization));
                assertCode(expected, request("PUT", api + "/" + id, body, authorization));
                assertCode(expected, request("DELETE", api + "/" + id, null, authorization));
            }
        }
    }

    @Test
    @DisplayName("直接调用Service仍执行读写角色校验，不能绕过Controller")
    void serviceChecksEveryEntryPoint() {
        Long job = seedJob(companyA, "服务权限", "1", "0");
        CompanySaveDTO companyDto = companyDto(companyBody());
        JobSaveDTO jobDto = jobDto(jobBody(companyA));
        authenticate("BUSINESS", businessId);
        assertNotNull(companyService.page(new CompanyQueryDTO()));
        assertNotNull(companyService.detail(companyB));
        assertNotNull(jobService.page(new JobQueryDTO()));
        assertNotNull(jobService.detail(job));
        assertBusinessCode(403, () -> companyService.create(companyDto));
        assertBusinessCode(403, () -> companyService.update(companyA, companyDto));
        assertBusinessCode(403, () -> companyService.delete(companyA));
        assertBusinessCode(403, () -> jobService.create(jobDto));
        assertBusinessCode(403, () -> jobService.update(job, jobDto));
        assertBusinessCode(403, () -> jobService.delete(job));
        authenticate("GRID_WORKER", businessId);
        assertBusinessCode(403, () -> companyService.page(new CompanyQueryDTO()));
        assertBusinessCode(403, () -> companyService.detail(companyA));
        assertBusinessCode(403, () -> companyService.create(companyDto));
        assertBusinessCode(403, () -> companyService.update(companyA, companyDto));
        assertBusinessCode(403, () -> companyService.delete(companyA));
        assertBusinessCode(403, () -> jobService.page(new JobQueryDTO()));
        assertBusinessCode(403, () -> jobService.detail(job));
        assertBusinessCode(403, () -> jobService.create(jobDto));
        assertBusinessCode(403, () -> jobService.update(job, jobDto));
        assertBusinessCode(403, () -> jobService.delete(job));
    }

    @Test
    @DisplayName("没有真实LoginUser的ADMIN主体不能写入缺失审计归属的数据")
    void serviceRejectsAdminWithoutLoginUserAuditIdentity() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("forged-admin", null, authorities));
        assertBusinessCode(401, () -> companyService.create(companyDto(companyBody())));
        assertBusinessCode(401, () -> companyService.update(companyA, companyDto(companyBody())));
        assertBusinessCode(401, () -> companyService.delete(companyA));
        assertBusinessCode(401, () -> jobService.create(jobDto(jobBody(companyA))));
        Long job = seedJob(companyA, "缺失登录主体", "1", "0");
        assertBusinessCode(401, () -> jobService.update(job, jobDto(jobBody(companyA))));
        assertBusinessCode(401, () -> jobService.delete(job));
    }

    @Test
    @DisplayName("企业必填名称与整数状态校验，不擅自扩展状态")
    void companyRequiredAndStatusValidation() throws Exception {
        for (Object name : new Object[]{null, "", "   "}) {
            Map<String, Object> body = companyBody();
            body.put("companyName", name);
            assertCode(400, request("POST", COMPANY, body, adminToken));
        }
        for (Object status : List.of(-1, 2, "正常")) {
            Map<String, Object> body = companyBody();
            body.put("status", status);
            assertCode(400, request("POST", COMPANY, body, adminToken));
        }
        Map<String, Object> disabled = companyBody();
        disabled.put("status", 0);
        Long id = createdId(COMPANY, disabled);
        assertEquals(0, number("company", "status", id));
        assertCode(400, request("GET", COMPANY + "/0", null, adminToken));
        assertCode(400, request("GET", COMPANY + "/page?status=2", null, adminToken));
    }

    @Test
    @DisplayName("企业字符串长度按实库限制校验，边界长度可以保存")
    void companyStringLengthValidation() throws Exception {
        Map<String, Integer> limits = Map.of("companyName", 100, "contactPerson", 50, "contactPhone", 20,
                "address", 255, "district", 20, "industry", 50, "remark", 500);
        for (var field : limits.entrySet()) {
            Map<String, Object> body = companyBody();
            body.put(field.getKey(), "长".repeat(field.getValue() + 1));
            assertCode(400, request("POST", COMPANY, body, adminToken));
        }
        Map<String, Object> body = companyBody();
        limits.forEach((field, length) -> { if (!field.equals("district")) body.put(field, "界".repeat(length)); });
        body.put("district", "10");
        Long id = createdId(COMPANY, body);
        assertEquals(100, text("company", "company_name", id).length());
        assertEquals(500, text("company", "remark", id).length());
        assertCode(400, request("GET", COMPANY + "/page?companyName=" + enc("长".repeat(101)), null, adminToken));
    }

    @Test
    @DisplayName("企业地区必须是启用字典值，失效与中文展示名称均拒绝")
    void companyDistrictDictionaryValidation() throws Exception {
        String disabled = "cj-d-" + run;
        seedDisabledDictionary("district", disabled);
        try {
            for (String district : List.of("不存在", "新城区", disabled)) {
                Map<String, Object> body = companyBody();
                body.put("district", district);
                assertCode(400, request("POST", COMPANY, body, adminToken));
                assertCode(400, request("GET", COMPANY + "/page?district=" + enc(district), null, businessToken));
            }
            Map<String, Object> normalized = companyBody();
            normalized.put("district", "1 ");
            Long id = createdId(COMPANY, normalized);
            assertEquals("1", text("company", "district", id));
        } finally {
            jdbc.update("DELETE FROM sys_dict WHERE dict_type='district' AND dict_value=?", disabled);
        }
    }

    @Test
    @DisplayName("岗位新增默认招聘中和1人，薪资字符串保留，审计由服务端维护")
    void createJobDefaultsAndServerAudit() throws Exception {
        Map<String, Object> body = jobBody(companyA);
        body.put("jobName", "  " + prefix + "新增岗位  ");
        body.put("salary", "3000~5000元/月，可面议");
        spoofAudit(body);
        Long id = createdId(JOB, body);
        assertEquals(prefix + "新增岗位", text("job", "job_name", id));
        assertEquals("3000~5000元/月，可面议", text("job", "salary", id));
        assertEquals("0", text("job", "status", id));
        assertEquals(1, number("job", "headcount", id));
        assertEquals(companyA.longValue(), number("job", "company_id", id));
        assertEquals(0, number("job", "deleted", id));
        assertServerAudit("job", id);
    }

    @Test
    @DisplayName("岗位详情同时返回完整岗位和企业联系信息，不暴露内部字段")
    void jobDetailReturnsCompleteJobAndCompanyInformation() throws Exception {
        Long job = seedJob(companyA, "详情岗位", "6", "0");
        JsonNode detail = data("GET", JOB + "/" + job, null, businessToken);
        assertJobFields(detail, job, companyA);
        assertEquals("测试联系人", detail.get("companyContactPerson").asText());
        assertEquals("13800000000", detail.get("companyContactPhone").asText());
        assertEquals("测试地址", detail.get("companyAddress").asText());
        assertEquals("1", detail.get("companyDistrict").asText());
        assertEquals("制造业", detail.get("companyIndustry").asText());
        JsonNode record = findRecord(page(JOB, "jobName=" + enc(prefix), businessToken), job);
        assertJobFields(record, job, companyA);
    }

    @Test
    @DisplayName("企业停用仍可查询已有岗位及企业信息，同企业普通更新允许")
    void disabledCompanyDoesNotHideExistingJobs() throws Exception {
        Long job = seedJob(companyB, "停用企业已有岗位", "1", "0");
        JsonNode detail = data("GET", JOB + "/" + job, null, businessToken);
        assertEquals(text("company", "company_name", companyB), detail.get("companyName").asText());
        assertEquals("测试联系人", detail.get("companyContactPerson").asText());
        assertEquals("2", detail.get("companyDistrict").asText());
        assertEquals("服务业", detail.get("companyIndustry").asText());
        assertEquals(companyB.longValue(), findRecord(page(JOB, "companyId=" + companyB, businessToken), job).get("companyId").asLong());
        Map<String, Object> body = jobBody(companyB);
        body.put("jobName", prefix + "停用后编辑原岗位");
        assertCode(200, request("PUT", JOB + "/" + job, body, adminToken));
    }

    @Test
    @DisplayName("岗位修改保留null及未传可选字段，不能伪造审计")
    void updateJobPreservesOptionalNullsAndCreationAudit() throws Exception {
        Long job = seedJob(companyA, "修改原岗位", "6", "1");
        Timestamp created = jdbc.queryForObject("SELECT create_time FROM job WHERE id=?", Timestamp.class, job);
        Map<String, Object> body = jobBody(companyA);
        body.put("jobName", prefix + "修改岗位");
        for (String field : List.of("salary", "requirement", "educationRequirement", "headcount", "status", "remark")) {
            body.put(field, null);
        }
        spoofAudit(body);
        assertCode(200, request("PUT", JOB + "/" + job, body, adminToken));
        JsonNode detail = data("GET", JOB + "/" + job, null, adminToken);
        assertEquals(prefix + "修改岗位", detail.get("jobName").asText());
        assertEquals("面议/按小时", detail.get("salary").asText());
        assertEquals("熟悉岗位技能", detail.get("requirement").asText());
        assertEquals("6", detail.get("educationRequirement").asText());
        assertEquals(3, detail.get("headcount").asInt());
        assertEquals("1", detail.get("status").asText());
        assertEquals("岗位备注", detail.get("remark").asText());
        assertEquals(-1, number("job", "create_by", job));
        assertEquals(adminId.longValue(), number("job", "update_by", job));
        assertEquals(created, jdbc.queryForObject("SELECT create_time FROM job WHERE id=?", Timestamp.class, job));
        assertCode(200, request("PUT", JOB + "/" + job,
                Map.of("companyId", companyA, "jobName", prefix + "只修改名称"), adminToken));
        assertEquals("岗位备注", text("job", "remark", job));
    }

    @Test
    @DisplayName("岗位分页支持各筛选、组合条件及完整列表信息")
    void jobPageFiltersAndCompleteRecords() throws Exception {
        Long a = seedJob(companyA, "岗位一", "6", "0");
        Long b = seedJob(companyA, "岗位二", "1", "1");
        Long c = seedJob(companyB, "岗位三", "6", "0");
        String base = "jobName=" + enc(prefix);
        assertEquals(2, page(JOB, base + "&companyId=" + companyA, businessToken).get("total").asInt());
        assertEquals(List.of(c, a), ids(page(JOB, base + "&educationRequirement=6", businessToken)));
        assertEquals(List.of(b), ids(page(JOB, base + "&status=1", adminToken)));
        assertEquals(List.of(a), ids(page(JOB, base + "&companyId=" + companyA + "&educationRequirement=6&status=0", adminToken)));
        JsonNode page = page(JOB, base, businessToken);
        assertJobFields(findRecord(page, a), a, companyA);
        assertJobFields(findRecord(page, c), c, companyB);
    }

    @Test
    @DisplayName("无筛选返回全部未删除岗位，分页大小与排序边界有效")
    void jobUnfilteredPageAndStablePagination() throws Exception {
        Long a = seedJob(companyA, "稳定一", "1", "0");
        Long b = seedJob(companyB, "稳定二", "1", "1");
        Long removed = seedJob(companyA, "已删除", "1", "0");
        jdbc.update("UPDATE job SET deleted=1 WHERE id=?", removed);
        jdbc.update("UPDATE job SET create_time=? WHERE id IN (?,?)", Timestamp.valueOf("2025-01-01 00:00:00"), a, b);
        JsonNode all = page(JOB, "", businessToken);
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE deleted=0", Long.class).longValue(), all.get("total").asLong());
        assertEquals(10, all.get("size").asLong());
        String filter = "jobName=" + enc(prefix);
        assertEquals(List.of(b, a), ids(page(JOB, filter + "&size=100", adminToken)));
        assertEquals(List.of(b), ids(page(JOB, filter + "&size=1", adminToken)));
        assertEquals(List.of(a), ids(page(JOB, filter + "&size=1&current=2", adminToken)));
        assertEquals(List.of(a), ids(page(JOB, filter + "&size=1&current=2", adminToken)));
        for (String bad : List.of("size=101", "size=0", "current=0", "companyId=0", "status=2")) {
            assertCode(400, request("GET", JOB + "/page?" + bad, null, adminToken));
        }
    }

    @Test
    @DisplayName("岗位新增只关联真实存在、未删除且启用的企业")
    void jobCreationRequiresAvailableCompany() throws Exception {
        Long removed = seedCompany("已删除企业", "1", "测试行业", 1);
        jdbc.update("UPDATE company SET deleted=1 WHERE id=?", removed);
        assertCode(404, request("POST", JOB, jobBody(Long.MAX_VALUE), adminToken));
        assertCode(404, request("POST", JOB, jobBody(removed), adminToken));
        assertCode(409, request("POST", JOB, jobBody(companyB), adminToken));
        Map<String, Object> missing = jobBody(companyA);
        missing.remove("companyId");
        assertCode(400, request("POST", JOB, missing, adminToken));
        assertCode(400, request("POST", JOB, jobBody(0L), adminToken));
        assertEquals(0, page(JOB, "jobName=" + enc(prefix), adminToken).get("total").asLong());
    }

    @Test
    @DisplayName("变更岗位所属企业时重做关联校验，失败不改变原岗位")
    void jobReparentingValidatesCompanyAndRollsBack() throws Exception {
        Long job = seedJob(companyA, "变更企业岗位", "1", "0");
        Long enabled = seedCompany("新启用企业", "3", "新行业", 1);
        assertCode(404, request("PUT", JOB + "/" + job, jobBody(Long.MAX_VALUE), adminToken));
        assertCode(409, request("PUT", JOB + "/" + job, jobBody(companyB), adminToken));
        assertEquals(companyA.longValue(), number("job", "company_id", job));
        Map<String, Object> body = jobBody(enabled);
        body.put("jobName", prefix + "已调入");
        assertCode(200, request("PUT", JOB + "/" + job, body, adminToken));
        JsonNode detail = data("GET", JOB + "/" + job, null, businessToken);
        assertEquals(enabled.longValue(), detail.get("companyId").asLong());
        assertEquals(prefix + "新启用企业", detail.get("companyName").asText());
    }

    @Test
    @DisplayName("岗位逻辑删除后不再查询、修改或重复删除")
    void jobLogicalDeleteAndMissingRecords() throws Exception {
        Long job = seedJob(companyA, "逻辑删除岗位", "1", "0");
        assertCode(200, request("DELETE", JOB + "/" + job, null, adminToken));
        assertEquals(1, number("job", "deleted", job));
        assertEquals(adminId.longValue(), number("job", "update_by", job));
        assertCode(404, request("GET", JOB + "/" + job, null, businessToken));
        assertCode(404, request("PUT", JOB + "/" + job, jobBody(companyA), adminToken));
        assertCode(404, request("DELETE", JOB + "/" + job, null, adminToken));
        assertCode(404, request("GET", JOB + "/9223372036854775807", null, adminToken));
        assertCode(400, request("GET", JOB + "/0", null, adminToken));
        assertEquals(0, page(JOB, "jobName=" + enc(prefix), businessToken).get("total").asLong());
    }

    @Test
    @DisplayName("企业存在任何未删除岗位时409，不级联删除；删除岗位后允许删除企业")
    void companyDeletionProtectsAllUndeletedJobs() throws Exception {
        Long job = seedJob(companyA, "已结束仍关联", "1", "1");
        assertCode(409, request("DELETE", COMPANY + "/" + companyA, null, adminToken));
        assertEquals(0, number("company", "deleted", companyA));
        assertEquals(0, number("job", "deleted", job));
        assertCode(200, request("DELETE", JOB + "/" + job, null, adminToken));
        assertCode(200, request("DELETE", COMPANY + "/" + companyA, null, adminToken));
        assertEquals(1, number("company", "deleted", companyA));
        assertEquals(1, number("job", "deleted", job));
    }

    @Test
    @DisplayName("岗位必填、人数及字符串状态严格按已确认业务值校验")
    void jobRequiredHeadcountAndStatusValidation() throws Exception {
        for (Object name : new Object[]{null, "", "   "}) {
            Map<String, Object> body = jobBody(companyA);
            body.put("jobName", name);
            assertCode(400, request("POST", JOB, body, adminToken));
        }
        for (int headcount : List.of(0, -1)) {
            Map<String, Object> body = jobBody(companyA);
            body.put("headcount", headcount);
            assertCode(400, request("POST", JOB, body, adminToken));
        }
        for (String status : List.of("-1", "2", "招聘中", "0 ")) {
            Map<String, Object> body = jobBody(companyA);
            body.put("status", status);
            assertCode(400, request("POST", JOB, body, adminToken));
        }
        Map<String, Object> ended = jobBody(companyA);
        ended.put("status", "1");
        ended.put("headcount", 2);
        Long id = createdId(JOB, ended);
        assertEquals("1", text("job", "status", id));
        assertEquals(2, number("job", "headcount", id));
    }

    @Test
    @DisplayName("岗位字符串字段按实库长度校验，薪资不转换为数值")
    void jobStringLengthsAndSalaryRemainString() throws Exception {
        Map<String, Integer> limits = Map.of("jobName", 100, "salary", 50, "educationRequirement", 20, "status", 20, "remark", 500);
        for (var field : limits.entrySet()) {
            Map<String, Object> body = jobBody(companyA);
            body.put(field.getKey(), "长".repeat(field.getValue() + 1));
            assertCode(400, request("POST", JOB, body, adminToken));
        }
        Map<String, Object> valid = jobBody(companyA);
        valid.put("jobName", "界".repeat(100));
        valid.put("salary", "薪".repeat(50));
        valid.put("remark", "注".repeat(500));
        Long id = createdId(JOB, valid);
        assertEquals("薪".repeat(50), text("job", "salary", id));
        assertEquals(100, text("job", "job_name", id).length());
        assertEquals(500, text("job", "remark", id).length());
        assertCode(400, request("GET", JOB + "/page?jobName=" + enc("长".repeat(101)), null, adminToken));
    }

    @Test
    @DisplayName("岗位要求按MySQL TEXT的UTF8字节上限校验，65535字节可以保存")
    void jobRequirementUtf8ByteLimit() throws Exception {
        String atLimit = "中".repeat(21845);
        assertEquals(65535, atLimit.getBytes(StandardCharsets.UTF_8).length);
        Map<String, Object> valid = jobBody(companyA);
        valid.put("requirement", atLimit);
        Long job = createdId(JOB, valid);
        assertEquals(atLimit, text("job", "requirement", job));
        Map<String, Object> invalid = jobBody(companyA);
        invalid.put("requirement", atLimit + "中");
        assertCode(400, request("POST", JOB, invalid, adminToken));
        invalid.put("requirement", "😀".repeat(16384));
        assertCode(400, request("PUT", JOB + "/" + job, invalid, adminToken));
        assertEquals(atLimit, text("job", "requirement", job));
    }

    @Test
    @DisplayName("学历要求使用启用字典值，中文、失效及尾空格值均拒绝")
    void jobEducationDictionaryValidation() throws Exception {
        String disabled = "cj-e-" + run;
        seedDisabledDictionary("education", disabled);
        try {
            for (String education : List.of("不存在", "本科", disabled, "1 ")) {
                Map<String, Object> body = jobBody(companyA);
                body.put("educationRequirement", education);
                assertCode(400, request("POST", JOB, body, adminToken));
                assertCode(400, request("GET", JOB + "/page?educationRequirement=" + enc(education), null, businessToken));
            }
            Map<String, Object> body = jobBody(companyA);
            body.put("educationRequirement", "7");
            Long id = createdId(JOB, body);
            assertEquals("7", text("job", "education_requirement", id));
        } finally {
            jdbc.update("DELETE FROM sys_dict WHERE dict_type='education' AND dict_value=?", disabled);
        }
    }

    @Test
    @DisplayName("直接调用Service同样执行参数校验，失败不写入岗位")
    void serviceValidatesInputsWithoutController() {
        authenticate("ADMIN", adminId);
        assertBusinessCode(400, () -> companyService.create(new CompanySaveDTO()));
        CompanySaveDTO companyDto = companyDto(companyBody());
        companyDto.setStatus(2);
        assertBusinessCode(400, () -> companyService.update(companyA, companyDto));
        JobSaveDTO jobDto = jobDto(jobBody(companyA));
        jobDto.setHeadcount(0);
        assertBusinessCode(400, () -> jobService.create(jobDto));
        jobDto.setHeadcount(1);
        jobDto.setEducationRequirement("1 ");
        assertBusinessCode(400, () -> jobService.create(jobDto));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE job_name LIKE ?", Integer.class, prefix + "%"));
    }

    @Test
    @DisplayName("企业行锁覆盖岗位新增及企业删除，新增提交后删除返回409")
    void companyDeletionWaitsForJobCreationTransaction() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch jobStaged = new CountDownLatch(1);
        CountDownLatch commitCreation = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        try {
            Future<Long> creating = executor.submit(() -> asAdmin(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertNotNull(companyMapper.selectForUpdate(companyA));
                Long id = jobService.create(jobDto(jobBody(companyA)));
                jobStaged.countDown();
                await(commitCreation);
                return id;
            })));
            assertTrue(jobStaged.await(10, TimeUnit.SECONDS));
            Future<Integer> deleting = executor.submit(() -> asAdmin(() -> {
                deletionStarted.countDown();
                try { companyService.delete(companyA); return 200; }
                catch (BusinessException e) { return e.getCode(); }
            }));
            assertTrue(deletionStarted.await(10, TimeUnit.SECONDS));
            // 未提交的新增持有企业行锁，删除不能跨过关联校验先行提交。
            assertThrows(TimeoutException.class, () -> deleting.get(200, TimeUnit.MILLISECONDS));
            commitCreation.countDown();
            Long job = creating.get(10, TimeUnit.SECONDS);
            assertEquals(409, deleting.get(10, TimeUnit.SECONDS));
            assertEquals(0, number("company", "deleted", companyA));
            assertEquals(companyA.longValue(), number("job", "company_id", job));
            assertEquals(0, number("job", "deleted", job));
        } finally {
            commitCreation.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("企业删除先持锁并提交后，并发岗位新增404且不产生孤立岗位")
    void jobCreationWaitsForCompanyDeletionTransaction() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deletionStaged = new CountDownLatch(1);
        CountDownLatch commitDeletion = new CountDownLatch(1);
        CountDownLatch creationStarted = new CountDownLatch(1);
        try {
            Future<?> deleting = executor.submit(() -> asAdmin(() -> new TransactionTemplate(transactionManager).execute(status -> {
                companyService.delete(companyA);
                deletionStaged.countDown();
                await(commitDeletion);
                return null;
            })));
            assertTrue(deletionStaged.await(10, TimeUnit.SECONDS));
            Future<Integer> creating = executor.submit(() -> asAdmin(() -> {
                creationStarted.countDown();
                try { jobService.create(jobDto(jobBody(companyA))); return 200; }
                catch (BusinessException e) { return e.getCode(); }
            }));
            assertTrue(creationStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> creating.get(200, TimeUnit.MILLISECONDS));
            commitDeletion.countDown();
            deleting.get(10, TimeUnit.SECONDS);
            assertEquals(404, creating.get(10, TimeUnit.SECONDS));
            assertEquals(1, number("company", "deleted", companyA));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE company_id=? AND deleted=0", Integer.class, companyA));
        } finally {
            commitDeletion.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private Long createUser(String username, String roleCode) {
        SysRolePO role = roleMapper.selectOne(new LambdaQueryWrapper<SysRolePO>().eq(SysRolePO::getRoleCode, roleCode));
        assertNotNull(role);
        SysUserPO user = new SysUserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        SysUserRolePO mapping = new SysUserRolePO();
        mapping.setUserId(user.getId());
        mapping.setRoleId(role.getId());
        userRoleMapper.insert(mapping);
        userIds.add(user.getId());
        return user.getId();
    }

    private Long seedCompany(String suffix, String district, String industry, int status) {
        String name = prefix + suffix;
        jdbc.update("INSERT INTO company(company_name,contact_person,contact_phone,address,district,industry,remark,status,deleted,create_by,update_by) VALUES (?,?,?,?,?,?,?,?,0,-1,-1)",
                name, "测试联系人", "13800000000", "测试地址", district, industry, "企业备注", status);
        return jdbc.queryForObject("SELECT id FROM company WHERE company_name=?", Long.class, name);
    }

    private Long seedJob(Long companyId, String suffix, String education, String status) {
        String name = prefix + suffix;
        jdbc.update("INSERT INTO job(company_id,job_name,salary,requirement,education_requirement,headcount,status,remark,deleted,create_by,update_by) VALUES (?,?,?,?,?,3,?,?,0,-1,-1)",
                companyId, name, "面议/按小时", "熟悉岗位技能", education, status, "岗位备注");
        return jdbc.queryForObject("SELECT id FROM job WHERE job_name=?", Long.class, name);
    }

    private void seedDisabledDictionary(String type, String value) {
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,status) VALUES (?,?,?,0)", type, value, "失效测试字典");
    }

    private Map<String, Object> companyBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", prefix + "保存企业");
        return body;
    }

    private Map<String, Object> jobBody(Long companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyId", companyId);
        body.put("jobName", prefix + "保存岗位");
        return body;
    }

    private void spoofAudit(Map<String, Object> body) {
        body.put("createBy", 987654321L);
        body.put("updateBy", 987654321L);
        body.put("createTime", "2000-01-01T00:00:00");
        body.put("updateTime", "2000-01-01T00:00:00");
        body.put("deleted", 1);
        body.put("id", Long.MAX_VALUE);
    }

    private void assertServerAudit(String table, Long id) {
        assertEquals(adminId.longValue(), number(table, "create_by", id));
        assertEquals(adminId.longValue(), number(table, "update_by", id));
        assertTrue(jdbc.queryForObject("SELECT create_time FROM " + table + " WHERE id=?", Timestamp.class, id)
                .toLocalDateTime().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
        assertTrue(jdbc.queryForObject("SELECT update_time FROM " + table + " WHERE id=?", Timestamp.class, id)
                .toLocalDateTime().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
    }

    private void assertCompanyFields(JsonNode record, Long id) {
        assertEquals(id.longValue(), record.get("id").asLong());
        for (String field : List.of("companyName", "contactPerson", "contactPhone", "address", "district", "industry", "remark", "status", "createTime", "updateTime")) {
            assertNotNull(record.get(field), field);
            assertFalse(record.get(field).isNull(), field);
        }
        assertEquals(text("company", "company_name", id), record.get("companyName").asText());
        assertEquals("测试联系人", record.get("contactPerson").asText());
        assertEquals("13800000000", record.get("contactPhone").asText());
        assertEquals("测试地址", record.get("address").asText());
        assertEquals("企业备注", record.get("remark").asText());
        assertHiddenInternalFields(record);
    }

    private void assertJobFields(JsonNode record, Long job, Long companyId) {
        assertEquals(job.longValue(), record.get("id").asLong());
        assertEquals(companyId.longValue(), record.get("companyId").asLong());
        assertEquals(text("company", "company_name", companyId), record.get("companyName").asText());
        for (String field : List.of("jobName", "salary", "requirement", "educationRequirement", "headcount", "status", "remark", "createTime", "updateTime")) {
            assertNotNull(record.get(field), field);
            assertFalse(record.get(field).isNull(), field);
        }
        assertEquals("面议/按小时", record.get("salary").asText());
        assertEquals("熟悉岗位技能", record.get("requirement").asText());
        assertEquals("岗位备注", record.get("remark").asText());
        assertHiddenInternalFields(record);
    }

    private void assertHiddenInternalFields(JsonNode record) {
        assertFalse(record.has("deleted"));
        assertFalse(record.has("createBy"));
        assertFalse(record.has("updateBy"));
    }

    private String text(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id=?", String.class, id);
    }

    private long number(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id=?", Long.class, id);
    }

    private void authenticate(String role, Long id) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        LoginUser login = new LoginUser(id, "company-job-test", "", true, authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, authorities));
    }

    private <T> T asAdmin(Callable<T> action) throws Exception {
        authenticate("ADMIN", adminId);
        try { return action.call(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private void assertBusinessCode(int expected, Executable operation) {
        assertEquals(expected, assertThrows(BusinessException.class, operation).getCode());
    }

    private CompanySaveDTO companyDto(Map<String, Object> body) {
        return objectMapper.convertValue(body, CompanySaveDTO.class);
    }

    private JobSaveDTO jobDto(Map<String, Object> body) {
        return objectMapper.convertValue(body, JobSaveDTO.class);
    }

    private Long createdId(String api, Map<String, Object> body) throws Exception {
        return data("POST", api, body, adminToken).asLong();
    }

    private String login(String username) throws Exception {
        return data("POST", "/api/auth/login", Map.of("username", username, "password", PASSWORD), null).get("token").asText();
    }

    private JsonNode page(String api, String query, String token) throws Exception {
        return data("GET", api + "/page" + (query.isEmpty() ? "" : "?" + query), null, token);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode record : page.get("records")) ids.add(record.get("id").asLong());
        return ids;
    }

    private JsonNode findRecord(JsonNode page, Long id) {
        for (JsonNode record : page.get("records")) if (record.get("id").asLong() == id) return record;
        fail("分页缺少记录: " + id);
        return null;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode data(String method, String path, Object body, String token) throws Exception {
        JsonNode response = request(method, path, body, token);
        assertCode(200, response);
        return response.get("data");
    }

    private void assertCode(int expected, JsonNode response) {
        assertEquals(expected, response.get("code").asInt(), response.toString());
    }

    private JsonNode request(String method, String path, Object body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(objectMapper.writeValueAsBytes(body)); }
            }
            int status = connection.getResponseCode();
            try (var input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                return objectMapper.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}

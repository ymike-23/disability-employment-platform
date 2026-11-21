package com.yzr.disability;

import com.yzr.disability.config.JwtUtil;
import com.yzr.disability.mapper.EmploymentServiceMapper;
import com.yzr.disability.service.TokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** 就业服务模块：HTTP、权限、状态及并发集成测试。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:employment_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmploymentServiceTest {

    private static final String API = "/api/employment-service";
    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private TokenService tokenService;
    @MockitoSpyBean private EmploymentServiceMapper serviceMapper;

    private final Map<Long, String> tokens = new ConcurrentHashMap<>();
    private final String run = Long.toString(System.nanoTime(), 36);
    private String adminToken;
    private String businessToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private long adminId;
    private long gridA;
    private long gridB;
    private long personA;
    private long personB;
    private long company;
    private long jobA;
    private long jobB;
    private int caseNumber;
    private String prefix;

    @BeforeAll
    void users() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:employment_test"));
        }
        adminId = user("admin", "ADMIN");
        long business = user("business", "BUSINESS");
        long otherBusiness = user("business_b", "BUSINESS");
        long noGrid = user("no_grid", "BUSINESS");
        long multiGrid = user("multi_grid", "BUSINESS");
        long worker = user("worker", "GRID_WORKER");
        gridA = grid(business);
        gridB = grid(otherBusiness);
        grid(multiGrid);
        grid(multiGrid);
        grid(worker);
        adminToken = tokens.get(adminId);
        businessToken = tokens.get(business);
        noGridToken = tokens.get(noGrid);
        multiGridToken = tokens.get(multiGrid);
        workerToken = tokens.get(worker);
    }

    @BeforeEach
    void fixtures() {
        when(tokenService.getToken(anyLong())).thenAnswer(invocation -> tokens.get(invocation.getArgument(0)));
        prefix = "就业" + run + "-" + (++caseNumber) + "-";
        personA = person("A", gridA);
        personB = person("B", gridB);
        company = company("企业", 1, 0);
        jobA = job(company, "岗位A", "0", 0);
        jobB = job(company, "岗位B", "0", 0);
    }

    @Test
    @DisplayName("历史就业状态保持NULL，推荐登记联查业务信息且只进入求职状态")
    void unknownStatusAndRecommendationAreSeparate() throws Exception {
        assertNull(employment(personA));
        JsonNode before = personHistory(personA, businessToken);
        assertTrue(before.get("employmentStatus").isNull());
        assertEquals(0, before.get("records").size());
        Map<String, Object> body = event(personA, jobA, null, "1");
        body.put("status", "2");
        body.put("result", "3");
        body.put("employmentStatus", "2");
        body.put("createBy", -1);
        body.put("deleted", 1);
        long root = create(body, adminToken);
        JsonNode detail = detail(root, businessToken);
        assertEquals("1", detail.get("serviceType").asText());
        assertEquals("1", detail.get("status").asText());
        assertEquals("1", detail.get("result").asText());
        assertEquals("1", detail.get("batchStage").asText());
        assertEquals("1", detail.get("employmentStatus").asText());
        assertEquals(personA, detail.get("personId").asLong());
        assertEquals(jobA, detail.get("jobId").asLong());
        assertEquals(company, detail.get("companyId").asLong());
        assertEquals(prefix + "A", detail.get("personName").asText());
        assertEquals(prefix + "企业", detail.get("companyName").asText());
        assertEquals(prefix + "岗位A", detail.get("jobName").asText());
        assertFalse(detail.has("deleted"));
        assertFalse(detail.has("createBy"));
        assertFalse(detail.has("updateBy"));
        assertEquals(adminId, jdbc.queryForObject("SELECT create_by FROM employment_service WHERE id=?", Long.class, root));
        assertEquals(0, number(root, "deleted"));
        assertEquals("1", employment(personA));
        assertNull(employment(personB));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM disabled_person WHERE id=?", Integer.class, personA));
    }

    @Test
    @DisplayName("完整就业流程、多轮联系面试及回访不混淆办理状态和人员就业状态")
    void fullLifecycleAndRepeatedEventsPreserveProgress() throws Exception {
        long root = recommend(personA, jobA);
        long failedContact = add(root, personA, jobA, "2");
        complete(failedContact, "2");
        assertStage(root, "1");
        finish(root, personA, jobA, "2", "1");
        assertStage(root, "2");
        finish(root, personA, jobA, "3", "1");
        assertStage(root, "3");
        finish(root, personA, jobA, "2", "1");
        assertStage(root, "3");
        finish(root, personA, jobA, "3", "1");
        finish(root, personA, jobA, "6", "1");
        assertStage(root, "4");
        assertEquals("1", employment(personA));
        long onboard = add(root, personA, jobA, "4");
        assertEquals("0", text(onboard, "status"));
        assertEquals("1", employment(personA));
        complete(onboard, "1");
        assertStage(root, "5");
        assertEquals("2", employment(personA));
        finish(root, personA, jobA, "5", "2");
        assertStage(root, "5");
        assertEquals("2", employment(personA));
        finish(root, personA, jobA, "7", "1");
        assertStage(root, "6");
        assertEquals("3", employment(personA));
        finish(root, personA, jobA, "5", "1");
        assertStage(root, "6");
        assertEquals("3", employment(personA));
        long next = recommend(personA, jobA);
        assertNotEquals(root, next);
        assertStage(root, "6");
        assertEquals("1", employment(personA));
        assertTrue(personHistory(personA, businessToken).get("records").size() >= 11);
    }

    @Test
    @DisplayName("不允许跳过阶段、重复待办、重复完成或把子事件当作批次")
    void invalidTransitionsCannotAdvance() throws Exception {
        long root = recommend(personA, jobA);
        for (String type : List.of("3", "6", "4", "7", "5")) {
            assertCode(409, request("POST", API, event(personA, jobA, root, type), businessToken));
        }
        long contact = add(root, personA, jobA, "2");
        assertCode(409, request("POST", API, event(personA, jobA, root, "2"), businessToken));
        assertCode(400, request("POST", API, event(personA, jobA, contact, "2"), businessToken));
        complete(contact, "1");
        assertCode(409, request("POST", API + "/" + contact + "/complete", Map.of("result", "1"), businessToken));
        assertCode(409, request("POST", API + "/" + contact + "/cancel", null, businessToken));
        finish(root, personA, jobA, "3", "1");
        finish(root, personA, jobA, "6", "1");
        assertCode(409, request("POST", API, event(personA, jobA, root, "2"), businessToken));
        assertCode(409, request("POST", API, event(personA, jobA, root, "3"), businessToken));
        assertEquals("1", employment(personA));
    }

    @Test
    @DisplayName("失败或放弃结束批次，取消仅关闭待办，结束后可重新推荐并保留历史")
    void failureWithdrawalAndCancellationHaveDifferentEffects() throws Exception {
        long first = recommend(personA, jobA);
        long cancelled = add(first, personA, jobA, "2");
        assertCode(200, request("POST", API + "/" + cancelled + "/cancel", null, businessToken));
        assertEquals("2", text(cancelled, "status"));
        assertStage(first, "1");
        assertEquals("1", employment(personA));
        finish(first, personA, jobA, "2", "1");
        finish(first, personA, jobA, "3", "2");
        assertStage(first, "7");
        assertEquals("0", employment(personA));
        long second = recommend(personA, jobA);
        finish(second, personA, jobA, "2", "3");
        assertStage(second, "7");
        assertEquals("0", employment(personA));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM employment_service WHERE person_id=? AND root_service_id IS NULL", Integer.class, personA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM employment_service WHERE person_id=? AND deleted<>0", Integer.class, personA));
        assertCode(405, request("DELETE", API + "/" + first, null, adminToken));
    }

    @Test
    @DisplayName("多个岗位可同时推荐，但重复推荐和第二个在职批次被拒绝")
    void multipleApplicationsAllowOnlyOneEmployment() throws Exception {
        long a = readyForOnboard(personA, jobA);
        long b = readyForOnboard(personA, jobB);
        assertCode(409, request("POST", API, event(personA, jobA, null, "1"), adminToken));
        long onboardA = add(a, personA, jobA, "4");
        long onboardB = add(b, personA, jobB, "4");
        complete(onboardA, "1");
        assertCode(409, request("POST", API + "/" + onboardB + "/complete", Map.of("result", "1"), adminToken));
        assertEquals("0", text(onboardB, "status"));
        assertStage(a, "5");
        assertStage(b, "4");
        assertEquals("2", employment(personA));
        finish(a, personA, jobA, "7", "1");
        assertEquals("1", employment(personA));
        complete(onboardB, "1");
        assertEquals("2", employment(personA));
    }

    @Test
    @DisplayName("分页和人员历史按真实网格限制，总数及事件筛选一致")
    void paginationAndHistoryRespectScope() throws Exception {
        long a = recommend(personA, jobA);
        long contact = add(a, personA, jobA, "2");
        long b = recommend(personB, jobB);
        JsonNode page = page("personId=" + personA + "&size=1", businessToken);
        assertEquals(2, page.get("total").asLong());
        assertEquals(2, page.get("pages").asLong());
        assertEquals(1, page.get("records").size());
        JsonNode batch = page("rootServiceId=" + a, businessToken);
        assertEquals(2, batch.get("total").asLong());
        assertEquals(List.of(contact), ids(page("personId=" + personA + "&status=0&serviceType=2", businessToken)));
        assertEquals(List.of(b), ids(page("personId=" + personB, adminToken)));
        assertEquals(2, personHistory(personA, businessToken).get("records").size());
        assertCode(403, request("GET", API + "/page?personId=" + personB, null, businessToken));
        assertCode(403, request("GET", API + "/person/" + personB, null, businessToken));
        assertCode(200, request("GET", API + "/person/" + personB, null, adminToken));
    }

    @Test
    @DisplayName("所有写入按真实归属授权，伪造人员岗位或批次不能跨网格操作")
    void writeOwnershipCannotBeSpoofed() throws Exception {
        long own = recommend(personA, jobA);
        long foreign = recommend(personB, jobB);
        long foreignContact = create(event(personB, jobB, foreign, "2"), adminToken);
        assertCode(403, request("GET", API + "/" + foreign, null, businessToken));
        assertCode(403, request("POST", API, event(personB, jobA, null, "1"), businessToken));
        assertCode(403, request("POST", API, event(personA, jobB, foreign, "2"), businessToken));
        assertCode(403, request("PUT", API + "/" + foreignContact, event(personA, jobA, own, "2"), businessToken));
        assertCode(403, request("POST", API + "/" + foreignContact + "/complete", Map.of("result", "1"), businessToken));
        assertCode(403, request("POST", API + "/" + foreignContact + "/cancel", null, businessToken));
        assertCode(400, request("POST", API, event(personA, jobB, own, "2"), businessToken));
        assertEquals("0", text(foreignContact, "status"));
        assertEquals(personB, jdbc.queryForObject("SELECT person_id FROM employment_service WHERE id=?", Long.class, foreignContact));
        assertCode(403, request("POST", API, event(personA, jobA, Long.MAX_VALUE, "2"), businessToken));
        assertCode(404, request("POST", API, event(personA, jobA, Long.MAX_VALUE, "2"), adminToken));
    }

    @Test
    @DisplayName("无网格、多网格和无业务角色不获得人员服务权限")
    void missingMultipleGridsAndRolesAreDenied() throws Exception {
        long root = recommend(personA, jobA);
        long contact = add(root, personA, jobA, "2");
        assertEquals(0, page("", noGridToken).get("total").asLong());
        assertCode(403, request("GET", API + "/page", null, multiGridToken));
        for (String token : List.of(noGridToken, multiGridToken, workerToken)) {
            assertCode(403, request("GET", API + "/" + root, null, token));
            assertCode(403, request("GET", API + "/person/" + personA, null, token));
            assertCode(403, request("POST", API, event(personA, jobB, null, "1"), token));
            assertCode(403, request("PUT", API + "/" + contact, event(personA, jobA, root, "2"), token));
            assertCode(403, request("POST", API + "/" + contact + "/complete", Map.of("result", "1"), token));
            assertCode(403, request("POST", API + "/" + contact + "/cancel", null, token));
        }
        assertCode(403, request("GET", API + "/page", null, workerToken));
        assertCode(403, request("GET", API + "/candidate-jobs", null, workerToken));
        assertCode(401, request("GET", API + "/page", null, null));
        assertCode(401, request("POST", API, event(personA, jobB, null, "1"), null));
        assertEquals("0", text(contact, "status"));
    }

    @Test
    @DisplayName("不存在或逻辑删除的人员不可新增查看或修改服务")
    void missingAndDeletedPeopleAreDenied() throws Exception {
        assertCode(404, request("POST", API, event(Long.MAX_VALUE, jobA, null, "1"), adminToken));
        assertCode(403, request("POST", API, event(Long.MAX_VALUE, jobA, null, "1"), businessToken));
        long root = recommend(personA, jobA);
        long contact = add(root, personA, jobA, "2");
        jdbc.update("UPDATE disabled_person SET deleted=1 WHERE id=?", personA);
        assertCode(404, request("GET", API + "/" + root, null, adminToken));
        assertCode(403, request("GET", API + "/" + root, null, businessToken));
        assertCode(403, request("POST", API + "/" + contact + "/complete", Map.of("result", "1"), businessToken));
        assertCode(404, request("POST", API, event(personA, jobB, null, "1"), adminToken));
        assertEquals(0, page("personId=" + personA, adminToken).get("total").asLong());
    }

    @Test
    @DisplayName("候选岗位过滤无效企业岗位，普通岗位展示规则保持原样")
    void candidatesAndRecommendationsRequireRecruitingResources() throws Exception {
        long disabledCompany = company("停用", 0, 0);
        long deletedCompany = company("删除", 1, 1);
        long disabledJob = job(disabledCompany, "停用企业岗位", "0", 0);
        long orphanJob = job(deletedCompany, "删除企业岗位", "0", 0);
        long endedJob = job(company, "结束岗位", "1", 0);
        long deletedJob = job(company, "删除岗位", "0", 1);
        JsonNode candidates = data("GET", API + "/candidate-jobs?jobName=" + enc(prefix), null, businessToken);
        assertEquals(2, candidates.get("total").asLong());
        assertTrue(ids(candidates).containsAll(List.of(jobA, jobB)));
        for (long id : List.of(disabledJob, orphanJob, endedJob)) {
            JsonNode rejected = request("POST", API, event(personA, id, null, "1"), businessToken);
            assertTrue(List.of(404, 409).contains(rejected.get("code").asInt()), rejected.toString());
        }
        assertCode(404, request("POST", API, event(personA, deletedJob, null, "1"), businessToken));
        assertNull(employment(personA));
        assertEquals(0, count(personA));
    }

    @Test
    @DisplayName("仅待办可修改日期说明且不能改归属，参数字节边界及字典值受校验")
    void pendingEditsAndInputValidation() throws Exception {
        assertCode(400, request("POST", API, Map.of(), businessToken));
        for (String type : List.of("0", "录用", "8", "1 ")) {
            assertCode(400, request("POST", API, event(personA, jobA, null, type), businessToken));
        }
        Map<String, Object> invalid = event(personA, jobA, null, "1");
        invalid.put("content", "中".repeat(21846));
        assertCode(400, request("POST", API, invalid, businessToken));
        invalid.put("content", "正常");
        invalid.put("remark", "a".repeat(501));
        assertCode(400, request("POST", API, invalid, businessToken));
        assertNull(employment(personA));
        long root = recommend(personA, jobA);
        long contact = add(root, personA, jobA, "2");
        Map<String, Object> update = event(personA, jobA, root, "2");
        update.put("serviceDate", LocalDate.now().plusDays(1).toString());
        update.put("content", "中".repeat(21845));
        update.put("remark", "a".repeat(500));
        assertCode(200, request("PUT", API + "/" + contact, update, businessToken));
        assertEquals(65535, text(contact, "content").getBytes(StandardCharsets.UTF_8).length);
        assertEquals(500, text(contact, "remark").length());
        Map<String, Object> keep = event(personA, jobA, root, "2");
        keep.put("content", null);
        assertCode(200, request("PUT", API + "/" + contact, keep, businessToken));
        assertEquals(65535, text(contact, "content").getBytes(StandardCharsets.UTF_8).length);
        keep.put("jobId", jobB);
        assertCode(400, request("PUT", API + "/" + contact, keep, businessToken));
        assertCode(400, request("POST", API + "/" + contact + "/complete", Map.of("result", "成功"), businessToken));
        assertCode(400, request("POST", API + "/" + contact + "/complete", Map.of("result", "1", "serviceDate", LocalDate.now().plusDays(1).toString()), businessToken));
        complete(contact, "1");
        assertCode(409, request("PUT", API + "/" + contact, update, businessToken));
        for (String query : List.of("current=0", "size=101", "personId=0", "rootServiceId=0", "status=完成", "serviceType=面试")) {
            assertCode(400, request("GET", API + "/page?" + encQuery(query), null, adminToken));
        }
    }

    @Test
    @DisplayName("停用结果字典不能完成业务事件")
    void disabledResultDictionaryCannotBeStored() throws Exception {
        long root = recommend(personA, jobA);
        long contact = add(root, personA, jobA, "2");
        jdbc.update("UPDATE sys_dict SET status=0 WHERE dict_type='employment_service_result' AND dict_value='1'");
        try {
            assertCode(400, request("POST", API + "/" + contact + "/complete", Map.of("result", "1"), businessToken));
            assertEquals("0", text(contact, "status"));
            assertStage(root, "1");
        } finally {
            jdbc.update("UPDATE sys_dict SET status=1 WHERE dict_type='employment_service_result' AND dict_value='1'");
        }
    }

    @Test
    @DisplayName("历史资源停用逻辑删除后仍可离职回访并显示历史归属")
    void historySurvivesResourceDeletion() throws Exception {
        long root = readyForOnboard(personA, jobA);
        finish(root, personA, jobA, "4", "1");
        jdbc.update("UPDATE job SET deleted=1,status='1' WHERE id=?", jobA);
        jdbc.update("UPDATE company SET deleted=1,status=0 WHERE id=?", company);
        finish(root, personA, jobA, "5", "1");
        finish(root, personA, jobA, "7", "1");
        finish(root, personA, jobA, "5", "1");
        JsonNode history = detail(root, businessToken);
        assertEquals(prefix + "企业", history.get("companyName").asText());
        assertEquals(prefix + "岗位A", history.get("jobName").asText());
        assertEquals("6", history.get("batchStage").asText());
        assertEquals("3", employment(personA));
    }

    @Test
    @DisplayName("被就业记录引用的岗位不能转移企业")
    void referencedJobCannotChangeCompany() throws Exception {
        recommend(personA, jobA);
        long target = company("新企业", 1, 0);
        assertCode(409, request("PUT", "/api/job/" + jobA, Map.of("companyId", target, "jobName", prefix + "改归属"), adminToken));
        assertEquals(company, jdbc.queryForObject("SELECT company_id FROM job WHERE id=?", Long.class, jobA));
    }

    @Test
    @DisplayName("并发推荐同一人员岗位只允许一个活动批次")
    void concurrentRecommendationsKeepOneBatch() throws Exception {
        List<JsonNode> responses = parallel(List.of(
                () -> request("POST", API, event(personA, jobA, null, "1"), businessToken),
                () -> request("POST", API, event(personA, jobA, null, "1"), adminToken)));
        assertSuccessAndConflict(responses);
        assertEquals(1, count(personA));
        assertEquals("1", employment(personA));
    }

    @Test
    @DisplayName("同一人员并发推荐不同岗位均可成功")
    void concurrentRecommendationsAllowDifferentJobs() throws Exception {
        List<JsonNode> responses = parallel(List.of(
                () -> request("POST", API, event(personA, jobA, null, "1"), businessToken),
                () -> request("POST", API, event(personA, jobB, null, "1"), adminToken)));
        for (JsonNode response : responses) assertCode(200, response);
        assertEquals(2, count(personA));
        assertEquals("1", employment(personA));
    }

    @Test
    @DisplayName("两个批次并发入职最多一个成功，失败事件保持待办")
    void concurrentOnboardingKeepsOneEmployment() throws Exception {
        long a = readyForOnboard(personA, jobA);
        long b = readyForOnboard(personA, jobB);
        long first = add(a, personA, jobA, "4");
        long second = add(b, personA, jobB, "4");
        List<JsonNode> responses = parallel(List.of(
                () -> request("POST", API + "/" + first + "/complete", Map.of("result", "1"), businessToken),
                () -> request("POST", API + "/" + second + "/complete", Map.of("result", "1"), adminToken)));
        assertSuccessAndConflict(responses);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM employment_service WHERE person_id=? AND service_type='4' AND status='1' AND result='1'", Integer.class, personA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM employment_service WHERE person_id=? AND service_type='4' AND status='0'", Integer.class, personA));
        assertEquals("2", employment(personA));
    }

    @Test
    @DisplayName("人员状态写入异常时新增服务和人员状态一起回滚")
    void statusFailureRollsBackEvent() throws Exception {
        doThrow(new IllegalStateException("test status update failure")).when(serviceMapper)
                .updatePersonEmploymentStatus(eq(personA), anyString(), anyLong());
        try {
            assertCode(500, request("POST", API, event(personA, jobA, null, "1"), businessToken));
            assertEquals(0, count(personA));
            assertNull(employment(personA));
        } finally {
            reset(serviceMapper);
        }
        recommend(personA, jobA);
        assertEquals(1, count(personA));
        assertEquals("1", employment(personA));
    }

    @Test
    @DisplayName("入职完成同步人员状态失败时办理状态及结果一起回滚")
    void statusFailureRollsBackCompletion() throws Exception {
        long root = readyForOnboard(personA, jobA);
        long onboard = add(root, personA, jobA, "4");
        doThrow(new IllegalStateException("test completion status update failure")).when(serviceMapper)
                .updatePersonEmploymentStatus(eq(personA), anyString(), anyLong());
        try {
            assertCode(500, request("POST", API + "/" + onboard + "/complete", Map.of("result", "1"), businessToken));
            assertEquals("0", text(onboard, "status"));
            assertNull(text(onboard, "result"));
            assertStage(root, "4");
            assertEquals("1", employment(personA));
        } finally {
            reset(serviceMapper);
        }
        complete(onboard, "1");
        assertStage(root, "5");
        assertEquals("2", employment(personA));
    }

    private long user(String label, String role) {
        String username = "employment_" + label + "_" + run;
        long id = insert("INSERT INTO sys_user(username,password,status,deleted) VALUES(?,?,1,0)", username, "unused");
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=? AND deleted=0", Long.class, role);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, roleId);
        tokens.put(id, jwtUtil.generateToken(id, username));
        return id;
    }

    private long grid(long userId) {
        return insert("INSERT INTO sys_grid(grid_name,grid_worker_id,status,deleted) VALUES(?,?,1,0)", "就业测试网格", userId);
    }

    private long person(String label, long gridId) {
        return insert("INSERT INTO disabled_person(name,grid_id,status,deleted) VALUES(?,?,1,0)", prefix + label, gridId);
    }

    private long company(String label, int status, int deleted) {
        return insert("INSERT INTO company(company_name,status,deleted) VALUES(?,?,?)", prefix + label, status, deleted);
    }

    private long job(long companyId, String label, String status, int deleted) {
        return insert("INSERT INTO job(company_id,job_name,status,deleted) VALUES(?,?,?,?)", companyId, prefix + label, status, deleted);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private Map<String, Object> event(long person, long job, Long root, String type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", person);
        body.put("jobId", job);
        body.put("rootServiceId", root);
        body.put("serviceType", type);
        return body;
    }

    private long recommend(long person, long job) throws Exception {
        return create(event(person, job, null, "1"), adminToken);
    }

    private long add(long root, long person, long job, String type) throws Exception {
        return create(event(person, job, root, type), adminToken);
    }

    private long finish(long root, long person, long job, String type, String result) throws Exception {
        long id = add(root, person, job, type);
        complete(id, result);
        return id;
    }

    private void complete(long id, String result) throws Exception {
        assertCode(200, request("POST", API + "/" + id + "/complete", Map.of("result", result), adminToken));
    }

    private long readyForOnboard(long person, long job) throws Exception {
        long root = recommend(person, job);
        finish(root, person, job, "2", "1");
        finish(root, person, job, "3", "1");
        finish(root, person, job, "6", "1");
        return root;
    }

    private JsonNode detail(long id, String token) throws Exception {
        return data("GET", API + "/" + id, null, token);
    }

    private void assertStage(long root, String expected) throws Exception {
        assertEquals(expected, detail(root, adminToken).get("batchStage").asText());
    }

    private JsonNode personHistory(long person, String token) throws Exception {
        return data("GET", API + "/person/" + person, null, token);
    }

    private String employment(long person) {
        return jdbc.queryForObject("SELECT employment_status FROM disabled_person WHERE id=?", String.class, person);
    }

    private long count(long person) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM employment_service WHERE person_id=?", Long.class, person);
    }

    private int number(long id, String field) {
        return jdbc.queryForObject("SELECT " + field + " FROM employment_service WHERE id=?", Integer.class, id);
    }

    private String text(long id, String field) {
        return jdbc.queryForObject("SELECT " + field + " FROM employment_service WHERE id=?", String.class, id);
    }

    private long create(Object body, String token) throws Exception {
        return data("POST", API, body, token).asLong();
    }

    private JsonNode page(String query, String token) throws Exception {
        return data("GET", API + "/page?" + query, null, token);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : page.get("records")) ids.add(row.get("id").asLong());
        return ids;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encQuery(String value) {
        int index = value.indexOf('=');
        return value.substring(0, index + 1) + enc(value.substring(index + 1));
    }

    private JsonNode data(String method, String path, Object body, String token) throws Exception {
        JsonNode response = request(method, path, body, token);
        assertCode(200, response);
        return response.get("data");
    }

    private void assertCode(int code, JsonNode response) {
        assertEquals(code, response.get("code").asInt(), response.toString());
    }

    private void assertSuccessAndConflict(List<JsonNode> responses) {
        assertEquals(1, responses.stream().filter(r -> r.get("code").asInt() == 200).count(), responses.toString());
        assertEquals(1, responses.stream().filter(r -> r.get("code").asInt() == 409).count(), responses.toString());
    }

    private List<JsonNode> parallel(List<Callable<JsonNode>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (Callable<JsonNode> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return call.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<JsonNode> responses = new ArrayList<>();
            for (Future<JsonNode> future : futures) responses.add(future.get(15, TimeUnit.SECONDS));
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private JsonNode request(String method, String path, Object body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(objectMapper.writeValueAsBytes(body));
                }
            }
            int status = connection.getResponseCode();
            try (var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                return objectMapper.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}

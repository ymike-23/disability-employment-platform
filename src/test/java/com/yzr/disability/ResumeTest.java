package com.yzr.disability;

import com.yzr.disability.config.JwtUtil;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.when;

/** 真实 HTTP/JWT 与 H2 集成测试；仅替换 Token 会话存储，不连接 Redis 或开发数据库。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:resume_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResumeTest {

    private static final String API = "/api/resume";
    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private TokenService tokenService;

    private final Map<Long, String> tokens = new ConcurrentHashMap<>();
    private final String run = Long.toString(System.nanoTime(), 36);
    private String adminToken;
    private String businessToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private long gridA;
    private long gridB;
    private long personA;
    private long personB;
    private int caseNumber;
    private String prefix;

    @BeforeAll
    void users() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:resume_test"));
        }
        long admin = user("admin", "ADMIN");
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
        adminToken = tokens.get(admin);
        businessToken = tokens.get(business);
        noGridToken = tokens.get(noGrid);
        multiGridToken = tokens.get(multiGrid);
        workerToken = tokens.get(worker);
    }

    @BeforeEach
    void fixtures() {
        when(tokenService.getToken(anyLong())).thenAnswer(invocation -> tokens.get(invocation.getArgument(0)));
        prefix = "简历" + run + "-" + (++caseNumber) + "-";
        personA = person("A", gridA);
        personB = person("B", gridB);
    }

    @Test
    @DisplayName("新增、详情、修改联查人员资料，保护内部字段并支持人工维护和清空")
    void createReadUpdateAndClearFields() throws Exception {
        Map<String, Object> body = body(personA);
        body.put("jobIntention", "文员");
        body.put("expectedSalary", "3000-5000");
        body.put("selfEvaluation", "工作认真");
        body.put("skillsSummary", "人工技能");
        body.put("deleted", 1);
        body.put("id", -1);
        long id = create(body, businessToken);
        JsonNode detail = data("GET", API + "/" + id, null, businessToken);
        assertEquals(personA, detail.get("personId").asLong());
        assertEquals(prefix + "A", detail.get("name").asText());
        assertEquals("1", detail.get("gender").asText());
        assertEquals("1990-01-02", detail.get("birthDate").asText());
        assertEquals("13800000000", detail.get("phone").asText());
        assertEquals("1", detail.get("district").asText());
        assertEquals(gridA, detail.get("gridId").asLong());
        assertEquals("测试地址", detail.get("address").asText());
        assertEquals("1", detail.get("disabilityType").asText());
        assertEquals("2", detail.get("disabilityLevel").asText());
        assertEquals("6", detail.get("education").asText());
        assertEquals("文员", detail.get("jobIntention").asText());
        assertEquals("3000-5000", detail.get("expectedSalary").asText());
        assertEquals("工作认真", detail.get("selfEvaluation").asText());
        assertEquals("人工技能", detail.get("skillsSummary").asText());
        assertNotNull(detail.get("createTime"));
        assertNotNull(detail.get("updateTime"));
        assertFalse(detail.has("deleted"));
        assertFalse(detail.has("idCard"));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM resume WHERE id=?", Integer.class, id));

        Map<String, Object> update = body(personA);
        update.put("jobIntention", "客服");
        update.put("selfEvaluation", null);
        assertCode(200, request("PUT", API + "/" + id, update, businessToken));
        JsonNode updated = data("GET", API + "/" + id, null, adminToken);
        assertEquals("客服", updated.get("jobIntention").asText());
        assertEquals("3000-5000", updated.get("expectedSalary").asText());
        assertEquals("工作认真", updated.get("selfEvaluation").asText());
        assertEquals("人工技能", updated.get("skillsSummary").asText());
        for (String field : List.of("jobIntention", "expectedSalary", "selfEvaluation", "skillsSummary")) {
            update.put(field, "");
        }
        assertCode(200, request("PUT", API + "/" + id, update, businessToken));
        JsonNode cleared = data("GET", API + "/" + id, null, businessToken);
        for (String field : List.of("jobIntention", "expectedSalary", "selfEvaluation", "skillsSummary")) {
            assertEquals("", cleared.get(field).asText(), field);
        }
    }

    @Test
    @DisplayName("仅合并本人考核通过且明确确认的就业技能，不根据培训名称推断")
    void generateSkillsFromConfirmedEmploymentTrainingOnly() throws Exception {
        training(personA, "B-电商培训", "2", "3", 0, 0, "1", "电商运营");
        training(personA, "A-按摩培训", "2", "3", 0, 0, "1", "按摩");
        training(personA, "重复按摩培训", "2", "3", 0, 0, "1", "按摩");
        for (String result : List.of("0", "2", "3")) {
            training(personA, "未通过" + result, "2", "3", 0, 0, result, "不应纳入");
        }
        training(personA, "无考核记录", "2", "3", 0, 0);
        training(personA, "无确认技能", "2", "3", 0, 0, "1", null);
        training(personA, "已取消", "2", "5", 0, 0, "1", "不应纳入");
        training(personA, "康复类", "1", "3", 0, 0, "1", "不应纳入");
        training(personA, "删除记录", "2", "3", 1, 0, "1", "不应纳入");
        training(personA, "删除计划保留已确认历史技能", "2", "3", 0, 1, "1", "历史技能");
        training(personB, "其他人员", "2", "3", 0, 0, "1", "不应纳入");
        long id = create(body(personA), businessToken);
        assertEquals("电商运营；按摩；历史技能", text(id, "skills_summary"));
        Map<String, Object> update = body(personA);
        update.put("skillsSummary", "人工补充技能");
        assertCode(200, request("PUT", API + "/" + id, update, businessToken));
        assertEquals("人工补充技能；电商运营；按摩；历史技能", text(id, "skills_summary"));
    }

    @Test
    @DisplayName("无确认技能可创建空摘要，人工摘要与已确认技能合并去重")
    void emptyAndManualSkillsAreSupported() throws Exception {
        long empty = create(body(personA), businessToken);
        assertEquals("", text(empty, "skills_summary"));
        training(personB, "不能根据名称猜测技能", "2", "3", 0, 0, "1", "实际技能");
        Map<String, Object> manual = body(personB);
        manual.put("skillsSummary", "自己维护");
        long manualId = create(manual, adminToken);
        assertEquals("自己维护；实际技能", text(manualId, "skills_summary"));
        long person = person("人工空摘要", gridA);
        training(person, "不应自动填入", "2", "3", 0, 0);
        Map<String, Object> explicitEmpty = body(person);
        explicitEmpty.put("skillsSummary", "");
        assertEquals("", text(create(explicitEmpty, businessToken), "skills_summary"));
    }

    @Test
    @DisplayName("一人仅一份当前简历，逻辑删除保留历史且允许再次创建")
    void duplicateAndDeletedResumeRules() throws Exception {
        long first = create(body(personA), businessToken);
        assertCode(409, request("POST", API, body(personA), adminToken));
        assertEquals(1, currentCount(personA));
        assertCode(200, request("DELETE", API + "/" + first, null, businessToken));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM resume WHERE id=?", Integer.class, first));
        assertCode(403, request("GET", API + "/" + first, null, businessToken));
        assertCode(404, request("GET", API + "/" + first, null, adminToken));
        long second = create(body(personA), adminToken);
        assertNotEquals(first, second);
        assertCode(200, request("DELETE", API + "/" + second, null, adminToken));
        long third = create(body(personA), businessToken);
        assertNotEquals(second, third);
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM resume WHERE person_id=?", Long.class, personA));
        assertEquals(1, currentCount(personA));
    }

    @Test
    @DisplayName("分页总数、人员和姓名筛选均遵守网格范围，ADMIN可全局管理")
    void pageFilteringAndAdminScope() throws Exception {
        long a = create(body(personA), adminToken);
        long b = create(body(personB), adminToken);
        long other = person("A2", gridA);
        long a2 = create(body(other), businessToken);
        JsonNode first = page("name=" + enc(prefix) + "&current=1&size=1", businessToken);
        JsonNode second = page("name=" + enc(prefix) + "&current=2&size=1", businessToken);
        assertEquals(2, first.get("total").asLong());
        assertEquals(2, first.get("pages").asLong());
        assertEquals(1, first.get("records").size());
        assertNotEquals(first.get("records").get(0).get("id").asLong(),
                second.get("records").get(0).get("id").asLong());
        assertEquals(List.of(a), ids(page("personId=" + personA, businessToken)));
        assertEquals(3, page("name=" + enc(prefix), adminToken).get("total").asLong());
        assertEquals(List.of(b), ids(page("personId=" + personB, adminToken)));
        assertEquals(List.of(a2), ids(page("name=" + enc(prefix + "A2"), businessToken)));
        assertCode(403, request("GET", API + "/page?personId=" + personB, null, businessToken));
        assertCode(200, request("PUT", API + "/" + b, body(personB), adminToken));
    }

    @Test
    @DisplayName("跨网格操作与伪造人员归属被拒绝，不能换人或覆盖原简历")
    void realOwnershipControlsReadWriteAndDelete() throws Exception {
        long own = create(body(personA), businessToken);
        long foreign = create(body(personB), adminToken);
        assertCode(403, request("GET", API + "/" + foreign, null, businessToken));
        assertCode(403, request("POST", API, body(personB), businessToken));
        assertCode(403, request("PUT", API + "/" + foreign, body(personA), businessToken));
        assertCode(403, request("DELETE", API + "/" + foreign, null, businessToken));
        assertCode(400, request("PUT", API + "/" + own, body(personB), adminToken));
        assertEquals(personA, jdbc.queryForObject("SELECT person_id FROM resume WHERE id=?", Long.class, own));
        assertEquals(personB, jdbc.queryForObject("SELECT person_id FROM resume WHERE id=?", Long.class, foreign));
        assertEquals(1, currentCount(personB));
    }

    @Test
    @DisplayName("无网格只读空列表，多网格拒绝访问，未认证与无业务角色均被拒绝")
    void missingMultipleGridsAndRolesAreRestricted() throws Exception {
        long id = create(body(personA), businessToken);
        assertEquals(0, page("", noGridToken).get("total").asLong());
        assertCode(403, request("GET", API + "/page", null, multiGridToken));
        for (String token : List.of(noGridToken, multiGridToken, workerToken)) {
            assertCode(403, request("GET", API + "/" + id, null, token));
            assertCode(403, request("POST", API, body(personA), token));
            assertCode(403, request("PUT", API + "/" + id, body(personA), token));
            assertCode(403, request("DELETE", API + "/" + id, null, token));
        }
        assertCode(403, request("GET", API + "/page", null, workerToken));
        assertCode(401, request("GET", API + "/page", null, null));
        assertCode(401, request("POST", API, body(personA), null));
        assertEquals(1, currentCount(personA));
    }

    @Test
    @DisplayName("不存在或已删除的人员不能新增、查看或修改当前简历")
    void missingAndDeletedPeopleAreNotAccessible() throws Exception {
        assertCode(404, request("POST", API, body(Long.MAX_VALUE), adminToken));
        assertCode(403, request("POST", API, body(Long.MAX_VALUE), businessToken));
        long id = create(body(personA), businessToken);
        jdbc.update("UPDATE disabled_person SET deleted=1 WHERE id=?", personA);
        assertCode(404, request("GET", API + "/" + id, null, adminToken));
        assertCode(403, request("GET", API + "/" + id, null, businessToken));
        assertCode(404, request("PUT", API + "/" + id, body(personA), adminToken));
        assertCode(404, request("POST", API, body(personA), adminToken));
        assertEquals(0, page("name=" + enc(prefix), adminToken).get("total").asLong());
    }

    @Test
    @DisplayName("必填人员、字符和TEXT字节上限、分页参数验证失败时不写入")
    void invalidInputDoesNotWrite() throws Exception {
        assertCode(400, request("POST", API, Map.of(), businessToken));
        assertCode(400, request("POST", API, body(0), businessToken));
        for (Map.Entry<String, String> invalid : Map.of(
                "jobIntention", "a".repeat(256),
                "expectedSalary", "a".repeat(51),
                "selfEvaluation", "中".repeat(21846),
                "skillsSummary", "😀".repeat(16384)).entrySet()) {
            Map<String, Object> body = body(personA);
            body.put(invalid.getKey(), invalid.getValue());
            assertCode(400, request("POST", API, body, businessToken));
        }
        assertEquals(0, currentCount(personA));
        long id = create(body(personA), businessToken);
        Map<String, Object> update = body(personA);
        update.put("skillsSummary", "中".repeat(21846));
        assertCode(400, request("PUT", API + "/" + id, update, businessToken));
        assertEquals("", text(id, "skills_summary"));
        assertCode(400, request("PUT", API + "/" + id, Map.of(), businessToken));
        for (String query : List.of("current=0", "size=0", "size=101", "personId=0", "name=" + "a".repeat(51))) {
            assertCode(400, request("GET", API + "/page?" + query, null, adminToken));
        }
        for (String method : List.of("GET", "PUT", "DELETE")) {
            assertCode(400, request(method, API + "/0", method.equals("PUT") ? body(personA) : null, adminToken));
        }
    }

    @Test
    @DisplayName("字段边界值允许存储，中文TEXT按UTF8字节计数")
    void exactFieldLimitsAreAccepted() throws Exception {
        Map<String, Object> body = body(personA);
        body.put("jobIntention", "a".repeat(255));
        body.put("expectedSalary", "a".repeat(50));
        body.put("selfEvaluation", "中".repeat(21845));
        body.put("skillsSummary", "a".repeat(65535));
        long id = create(body, businessToken);
        assertEquals(65535, text(id, "self_evaluation").getBytes(StandardCharsets.UTF_8).length);
        assertEquals(65535, text(id, "skills_summary").length());
    }

    @Test
    @DisplayName("无数据库唯一约束时，同一人员并发创建仅一次成功")
    void concurrentCreatesKeepOneCurrentResume() throws Exception {
        List<JsonNode> responses = parallel(List.of(
                () -> request("POST", API, body(personA), businessToken),
                () -> request("POST", API, body(personA), adminToken)));
        assertEquals(1, responses.stream().filter(r -> r.get("code").asInt() == 200).count(), responses.toString());
        assertEquals(1, responses.stream().filter(r -> r.get("code").asInt() == 409).count(), responses.toString());
        assertEquals(1, currentCount(personA));
    }

    private long user(String label, String role) {
        String username = "resume_" + label + "_" + run;
        long id = insert("INSERT INTO sys_user(username,password,status,deleted) VALUES(?,?,1,0)", username, "unused");
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=? AND deleted=0", Long.class, role);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, roleId);
        tokens.put(id, jwtUtil.generateToken(id, username));
        return id;
    }

    private long grid(long userId) {
        return insert("INSERT INTO sys_grid(grid_name,grid_worker_id,status,deleted) VALUES(?,?,1,0)", "简历测试网格", userId);
    }

    private long person(String label, long gridId) {
        return insert("INSERT INTO disabled_person(name,gender,id_card,birth_date,phone,district,grid_id,address,"
                        + "disability_type,disability_level,education,status,deleted) VALUES(?,?,?,'1990-01-02',?,?,?,?,?,?,?,1,0)",
                prefix + label, "1", "110101199001020010", "13800000000", "1", gridId, "测试地址", "1", "2", "6");
    }

    private void training(long personId, String name, String category, String status, int recordDeleted, int planDeleted) {
        training(personId, name, category, status, recordDeleted, planDeleted, null, null);
    }

    private void training(long personId, String name, String category, String status, int recordDeleted,
                          int planDeleted, String result, String skills) {
        long plan = insert("INSERT INTO training_plan(plan_name,category,status,publish_status,deleted) VALUES(?,?,'3','1',?)",
                name, category, planDeleted);
        jdbc.update("INSERT INTO training_record(person_id,plan_id,status,deleted,result,acquired_skills) VALUES(?,?,?,?,?,?)",
                personId, plan, status, recordDeleted, result, skills);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private Map<String, Object> body(long personId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", personId);
        return body;
    }

    private long currentCount(long personId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM resume WHERE person_id=? AND deleted=0", Long.class, personId);
    }

    private String text(long id, String field) {
        return jdbc.queryForObject("SELECT " + field + " FROM resume WHERE id=?", String.class, id);
    }

    private long create(Object body, String token) throws Exception {
        return data("POST", API, body, token).asLong();
    }

    private JsonNode page(String query, String token) throws Exception {
        return data("GET", API + "/page?" + query, null, token);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : page.get("records")) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode data(String method, String path, Object body, String token) throws Exception {
        JsonNode response = request(method, path, body, token);
        assertCode(200, response);
        return response.get("data");
    }

    private void assertCode(int code, JsonNode response) {
        assertEquals(code, response.get("code").asInt(), response.toString());
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
            for (Future<JsonNode> future : futures) {
                responses.add(future.get(15, TimeUnit.SECONDS));
            }
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
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
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

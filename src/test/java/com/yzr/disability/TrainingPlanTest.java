package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import static org.junit.jupiter.api.Assertions.*;

/** 培训计划接口、规则及网格权限集成测试，仅使用 H2 测试库。 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingPlanTest {

    private static final String API = "/api/training-plan";
    private static final String PWD = "Test@123456";
    private static final String JOB_TYPE = "就业技能";
    private static final String REHAB_TYPE = "康复训练";
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 5);

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrainingPlanMapper trainingPlanMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysGridMapper sysGridMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long adminUserId;
    private Long businessUserId;
    private Long businessBUserId;
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private String businessBToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private int caseNumber;
    private String planPrefix;
    private Long pendingAId;
    private Long ongoingAId;
    private Long endedAId;
    private Long pendingBId;
    private Long adminPlanId;
    private Long unownedPlanId;

    @BeforeAll
    void setupUsers() throws Exception {
        adminUserId = createUser("tp_test_admin", "ADMIN");
        businessUserId = createUser("tp_test_business_a", "BUSINESS");
        businessBUserId = createUser("tp_test_business_b", "BUSINESS");
        createUser("tp_test_no_grid", "BUSINESS");
        Long multiGridUserId = createUser("tp_test_multi_grid", "BUSINESS");
        Long workerUserId = createUser("tp_test_worker", "GRID_WORKER");
        gridAId = createGrid("培训计划测试网格A", businessUserId);
        gridBId = createGrid("培训计划测试网格B", businessBUserId);
        createGrid("培训计划测试多网格1", multiGridUserId);
        createGrid("培训计划测试多网格2", multiGridUserId);
        createGrid("培训计划测试非业务角色网格", workerUserId);
        adminToken = login("tp_test_admin");
        businessToken = login("tp_test_business_a");
        businessBToken = login("tp_test_business_b");
        noGridToken = login("tp_test_no_grid");
        multiGridToken = login("tp_test_multi_grid");
        workerToken = login("tp_test_worker");
    }

    @BeforeEach
    void setupPlans() {
        // 独立名称范围隔离每个用例，分页和更新不依赖执行顺序。
        planPrefix = "培训计划测试" + (++caseNumber) + "-";
        pendingAId = seedPlan("职业培训", businessUserId, "2", JOB_TYPE, "0", 8);
        ongoingAId = seedPlan("康复训练", businessUserId, "1", REHAB_TYPE, "1", 3);
        endedAId = seedPlan("计算机培训", businessUserId, "2", JOB_TYPE, "2", 0);
        pendingBId = seedPlan("其他网格就业", businessBUserId, "2", JOB_TYPE, "0", 2);
        adminPlanId = seedPlan("管理员计划", adminUserId, "1", REHAB_TYPE, "0", 0);
        unownedPlanId = seedPlan("历史无创建人计划", null, "1", REHAB_TYPE, "0", 0);
    }

    @AfterAll
    void cleanupTokens() {
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("新增培训计划成功，默认报名人数为0、状态为未开始")
    void createPlan() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("planName", "  " + planPrefix + "新增计划  ");
        body.put("category", "2");
        body.put("trainingType", JOB_TYPE);
        body.put("location", "社区培训室");
        body.put("organizer", "残疾人服务中心");
        body.put("remark", "周末培训");
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        TrainingPlanPO po = trainingPlanMapper.selectById(response.get("data").asLong());
        assertNotNull(po);
        assertEquals(planPrefix + "新增计划", po.getPlanName());
        assertEquals("2", po.getCategory());
        assertEquals(JOB_TYPE, po.getTrainingType());
        assertEquals(START, po.getStartDate());
        assertEquals(END, po.getEndDate());
        assertEquals(20, po.getCapacity());
        assertEquals(0, po.getEnrolledCount());
        assertEquals(TrainingPlanService.STATUS_NOT_STARTED, po.getStatus());
        assertEquals(0, po.getDeleted());
        assertEquals(businessUserId, po.getCreateBy());
        assertNotNull(po.getCreateTime());
        assertNotNull(po.getUpdateTime());
    }

    @Test
    @DisplayName("分页返回正确总数、页数及不同页记录")
    void pagePlans() throws Exception {
        JsonNode first = pageData(query("&current=1&size=1"), businessToken);
        JsonNode second = pageData(query("&current=2&size=1"), businessToken);
        assertEquals(3, first.get("total").asLong());
        assertEquals(3, first.get("pages").asLong());
        assertEquals(1, first.get("size").asLong());
        assertEquals(1, first.get("records").size());
        assertEquals(2, second.get("current").asLong());
        assertEquals(endedAId.longValue(), first.get("records").get(0).get("id").asLong());
        assertEquals(ongoingAId.longValue(), second.get("records").get(0).get("id").asLong());
    }

    @Test
    @DisplayName("计划名称、大类、培训类型及状态筛选同时生效")
    void pageFiltersPlan() throws Exception {
        JsonNode page = pageData(query("&category=2&trainingType=" + enc(JOB_TYPE) + "&status=0"), businessToken);
        assertEquals(1, page.get("total").asLong());
        assertEquals(List.of(pendingAId), idsOf(page));
        JsonNode named = pageData("planName=" + enc(planPrefix + "职业"), adminToken);
        assertEquals(List.of(pendingAId), idsOf(named));
        assertEquals(List.of(ongoingAId), idsOf(pageData(query("&category=1&status=1"), businessToken)));
    }

    @Test
    @DisplayName("详情返回计划字段，不暴露逻辑删除和审计账号字段")
    void detailPlan() throws Exception {
        JsonNode response = request("GET", API + "/" + pendingAId, null, businessToken);
        assertSuccess(response);
        JsonNode data = response.get("data");
        assertEquals(pendingAId.longValue(), data.get("id").asLong());
        assertEquals(START.toString(), data.get("startDate").asText());
        assertEquals(END.toString(), data.get("endDate").asText());
        assertEquals(20, data.get("capacity").asInt());
        assertEquals(8, data.get("enrolledCount").asInt());
        assertFalse(data.has("deleted"));
        assertFalse(data.has("createBy"));
    }

    @Test
    @DisplayName("修改成功，未传可选字段保留，并记录更新人")
    void updatePlan() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("planName", planPrefix + "更新后的计划");
        body.put("capacity", 25);
        body.put("status", TrainingPlanService.STATUS_IN_PROGRESS);
        body.put("location", "新的培训地点");
        assertSuccess(request("PUT", API + "/" + pendingAId, body, businessToken));
        TrainingPlanPO po = trainingPlanMapper.selectById(pendingAId);
        assertEquals(planPrefix + "更新后的计划", po.getPlanName());
        assertEquals(25, po.getCapacity());
        assertEquals(8, po.getEnrolledCount());
        assertEquals("新的培训地点", po.getLocation());
        assertEquals(TrainingPlanService.STATUS_IN_PROGRESS, po.getStatus());
        assertEquals(JOB_TYPE, po.getTrainingType());
        assertEquals("计划测试主办单位", po.getOrganizer());
        assertEquals(businessUserId, po.getCreateBy());
        assertEquals(businessUserId, po.getUpdateBy());
    }

    @Test
    @DisplayName("删除为逻辑删除，分页和详情排除已删除计划")
    void deletePlanLogically() throws Exception {
        assertSuccess(request("DELETE", API + "/" + pendingAId, null, businessToken));
        assertNull(trainingPlanMapper.selectById(pendingAId));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_plan WHERE id = ?", Integer.class, pendingAId));
        JsonNode page = pageData(query(""), businessToken);
        assertEquals(2, page.get("total").asLong());
        assertFalse(idsOf(page).contains(pendingAId));
        assertEquals(404, request("GET", API + "/" + pendingAId, null, adminToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + pendingAId, saveBody(), businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + pendingAId, null, businessToken).get("code").asInt());
    }

    @Test
    @DisplayName("ADMIN 无需网格，可以跨网格管理及访问历史计划")
    void adminCanManageAllPlans() throws Exception {
        assertEquals(6, pageData(query(""), adminToken).get("total").asLong());
        assertSuccess(request("GET", API + "/" + pendingBId, null, adminToken));
        assertSuccess(request("GET", API + "/" + unownedPlanId, null, adminToken));
        JsonNode create = request("POST", API, saveBody(), adminToken);
        assertSuccess(create);
        TrainingPlanPO created = trainingPlanMapper.selectById(create.get("data").asLong());
        assertEquals(adminUserId, created.getCreateBy());
        Map<String, Object> body = saveBody();
        body.put("remark", "管理员跨网格修改");
        assertSuccess(request("PUT", API + "/" + pendingBId, body, adminToken));
        assertEquals(adminUserId, trainingPlanMapper.selectById(pendingBId).getUpdateBy());
        assertSuccess(request("DELETE", API + "/" + pendingBId, null, adminToken));
        assertNull(trainingPlanMapper.selectById(pendingBId));
    }

    @Test
    @DisplayName("BUSINESS 只能访问其有效网格创建的计划")
    void businessPageIsScopedToGrid() throws Exception {
        JsonNode page = pageData(query(""), businessToken);
        assertEquals(3, page.get("total").asLong());
        assertTrue(idsOf(page).contains(pendingAId));
        assertFalse(idsOf(page).contains(pendingBId));
        assertFalse(idsOf(page).contains(adminPlanId));
        assertFalse(idsOf(page).contains(unownedPlanId));
        for (JsonNode record : page.get("records")) {
            assertEquals(businessUserId, trainingPlanMapper.selectById(record.get("id").asLong()).getCreateBy());
        }
        assertEquals(List.of(pendingBId), idsOf(pageData(query(""), businessBToken)));
        assertEquals(403, request("GET", API + "/" + adminPlanId, null, businessToken).get("code").asInt());
        assertEquals(403, request("GET", API + "/" + unownedPlanId, null, businessToken).get("code").asInt());
    }

    @Test
    @DisplayName("伪造人员、网格或创建人不能跨网格查询、修改或删除计划")
    void forgedScopeCannotBypassPermissions() throws Exception {
        Map<String, Object> forged = saveBody();
        forged.put("personId", Long.MAX_VALUE);
        forged.put("gridId", gridBId);
        forged.put("createBy", businessBUserId);
        forged.put("remark", "越权修改");
        assertEquals(403, request("GET", API + "/" + pendingBId + "?personId=" + Long.MAX_VALUE,
                null, businessToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + pendingBId, forged, businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + pendingBId, null, businessToken).get("code").asInt());
        TrainingPlanPO original = trainingPlanMapper.selectById(pendingBId);
        assertNotNull(original);
        assertEquals(businessBUserId, original.getCreateBy());
        assertEquals("计划测试备注", original.getRemark());
        JsonNode page = pageData(query("&personId=" + Long.MAX_VALUE + "&gridId=" + gridBId
                + "&createBy=" + businessBUserId), businessToken);
        assertEquals(3, page.get("total").asLong());
        assertFalse(idsOf(page).contains(pendingBId));

        JsonNode create = request("POST", API, forged, businessToken);
        assertSuccess(create);
        Long newId = create.get("data").asLong();
        assertEquals(businessUserId, trainingPlanMapper.selectById(newId).getCreateBy());
        assertEquals(403, request("GET", API + "/" + newId, null, businessBToken).get("code").asInt());
    }

    @Test
    @DisplayName("不存在的计划不能查询、修改或删除，BUSINESS 不泄露存在性")
    void missingPlanIsRejected() throws Exception {
        String path = API + "/" + Long.MAX_VALUE;
        assertEquals(404, request("GET", path, null, adminToken).get("code").asInt());
        assertEquals(404, request("PUT", path, saveBody(), adminToken).get("code").asInt());
        assertEquals(404, request("DELETE", path, null, adminToken).get("code").asInt());
        assertEquals(403, request("PUT", path, saveBody(), businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", path, null, businessToken).get("code").asInt());
        JsonNode missing = request("GET", path, null, businessToken);
        JsonNode forbidden = request("GET", API + "/" + pendingBId, null, businessToken);
        assertEquals(403, missing.get("code").asInt());
        assertEquals(missing.get("message").asText(), forbidden.get("message").asText());
    }

    @Test
    @DisplayName("日期倒置禁止新增和修改，开始日期等于结束日期允许")
    void datesAreValidated() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        Map<String, Object> body = saveBody();
        body.put("startDate", END.toString());
        body.put("endDate", START.toString());
        assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        assertEquals(400, request("PUT", API + "/" + pendingAId, body, businessToken).get("code").asInt());
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        assertEquals(START, trainingPlanMapper.selectById(pendingAId).getStartDate());
        assertEquals(END, trainingPlanMapper.selectById(pendingAId).getEndDate());
        body.put("startDate", START.toString());
        JsonNode create = request("POST", API, body, businessToken);
        assertSuccess(create);
        TrainingPlanPO po = trainingPlanMapper.selectById(create.get("data").asLong());
        assertEquals(po.getStartDate(), po.getEndDate());
    }

    @Test
    @DisplayName("容量为0或负数时禁止新增和修改")
    void capacityMustBePositive() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        for (int capacity : List.of(0, -1)) {
            Map<String, Object> body = saveBody();
            body.put("capacity", capacity);
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
            assertEquals(400, request("PUT", API + "/" + pendingAId, body, businessToken).get("code").asInt());
        }
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        assertEquals(20, trainingPlanMapper.selectById(pendingAId).getCapacity());
    }

    @Test
    @DisplayName("报名人数不能为负或超过容量，等于容量允许")
    void enrolledCountIsBounded() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        for (int enrolledCount : List.of(-1, 21)) {
            Map<String, Object> body = saveBody();
            body.put("enrolledCount", enrolledCount);
            assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
            assertEquals(400, request("PUT", API + "/" + pendingAId, body, businessToken).get("code").asInt());
        }
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        assertEquals(8, trainingPlanMapper.selectById(pendingAId).getEnrolledCount());
        Map<String, Object> full = saveBody();
        full.put("enrolledCount", 20);
        JsonNode create = request("POST", API, full, businessToken);
        assertSuccess(create);
        assertEquals(20, trainingPlanMapper.selectById(create.get("data").asLong()).getEnrolledCount());
        assertSuccess(request("PUT", API + "/" + pendingAId, full, businessToken));
        assertEquals(20, trainingPlanMapper.selectById(pendingAId).getEnrolledCount());
    }

    @Test
    @DisplayName("新容量不能小于当前报名人数，伪造更低报名人数不能绕过")
    void capacityCannotDropBelowCurrentEnrollments() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("capacity", 7);
        assertEquals(400, request("PUT", API + "/" + pendingAId, body, businessToken).get("code").asInt());
        body.put("enrolledCount", 0);
        assertEquals(400, request("PUT", API + "/" + pendingAId, body, businessToken).get("code").asInt());
        TrainingPlanPO unchanged = trainingPlanMapper.selectById(pendingAId);
        assertEquals(20, unchanged.getCapacity());
        assertEquals(8, unchanged.getEnrolledCount());
        body.remove("enrolledCount");
        body.put("capacity", 8);
        assertSuccess(request("PUT", API + "/" + pendingAId, body, businessToken));
        assertEquals(8, trainingPlanMapper.selectById(pendingAId).getCapacity());
        assertEquals(8, trainingPlanMapper.selectById(pendingAId).getEnrolledCount());
    }

    @Test
    @DisplayName("状态只接受数据库已有的0、1、2")
    void statusUsesExistingDefinition() throws Exception {
        Map<String, Object> body = saveBody();
        for (String status : List.of("0", "1", "2")) {
            body.put("status", status);
            assertSuccess(request("PUT", API + "/" + pendingAId, body, adminToken));
            assertEquals(status, trainingPlanMapper.selectById(pendingAId).getStatus());
        }
        body.put("status", "3");
        assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        assertEquals(400, request("PUT", API + "/" + pendingAId, body, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?status=3", null, adminToken).get("code").asInt());
        assertEquals("2", trainingPlanMapper.selectById(pendingAId).getStatus());
    }

    @Test
    @DisplayName("名称、日期及容量必填，文本长度及查询分页参数校验生效")
    void invalidInputIsRejected() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        for (String field : List.of("planName", "startDate", "endDate", "capacity")) {
            Map<String, Object> body = saveBody();
            body.remove(field);
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        }
        for (String name : List.of("", "   ", "长".repeat(101))) {
            Map<String, Object> body = saveBody();
            body.put("planName", name);
            assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        }
        Map<String, Object> invalidCategory = saveBody();
        invalidCategory.put("category", "3");
        assertEquals(400, request("POST", API, invalidCategory, businessToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?current=0", null, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?size=101", null, adminToken).get("code").asInt());
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
    }

    @Test
    @DisplayName("无网格返回空分页，详情及写操作拒绝，多网格配置返回403")
    void missingOrMultipleGridsAreRestricted() throws Exception {
        assertEquals(0, pageData(query(""), noGridToken).get("total").asLong());
        JsonNode multiPage = request("GET", API + "/page", null, multiGridToken);
        assertEquals(403, multiPage.get("code").asInt());
        assertEquals(GridScopeService.MULTI_GRID_MESSAGE, multiPage.get("message").asText());
        for (String token : List.of(noGridToken, multiGridToken)) {
            assertEquals(403, request("POST", API, saveBody(), token).get("code").asInt());
            assertEquals(403, request("GET", API + "/" + pendingAId, null, token).get("code").asInt());
            assertEquals(403, request("PUT", API + "/" + pendingAId, saveBody(), token).get("code").asInt());
            assertEquals(403, request("DELETE", API + "/" + pendingAId, null, token).get("code").asInt());
        }
        assertNotNull(trainingPlanMapper.selectById(pendingAId));
    }

    @Test
    @DisplayName("网格停用后，BUSINESS 无法查询或写入其创建的计划")
    void inactiveGridIsDenied() throws Exception {
        SysGridPO grid = new SysGridPO();
        grid.setId(gridAId);
        grid.setStatus(0);
        try {
            assertEquals(1, sysGridMapper.updateById(grid));
            assertEquals(0, pageData(query(""), businessToken).get("total").asLong());
            assertEquals(403, request("GET", API + "/" + pendingAId, null, businessToken).get("code").asInt());
            assertEquals(403, request("POST", API, saveBody(), businessToken).get("code").asInt());
            assertEquals(403, request("PUT", API + "/" + pendingAId, saveBody(), businessToken).get("code").asInt());
            assertEquals(403, request("DELETE", API + "/" + pendingAId, null, businessToken).get("code").asInt());
            assertSuccess(request("GET", API + "/" + pendingAId, null, adminToken));
        } finally {
            grid.setStatus(1);
            sysGridMapper.updateById(grid);
        }
    }

    @Test
    @DisplayName("未登录或无ADMIN/BUSINESS角色时拒绝访问")
    void authenticationAndRolesAreRequired() throws Exception {
        assertEquals(401, request("GET", API + "/page", null, null).get("code").asInt());
        assertEquals(403, request("GET", API + "/page", null, workerToken).get("code").asInt());
        assertEquals(403, request("GET", API + "/" + pendingAId, null, workerToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(), workerToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + pendingAId, saveBody(), workerToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + pendingAId, null, workerToken).get("code").asInt());
    }

    private Long createUser(String username, String roleCode) {
        LambdaQueryWrapper<SysRolePO> query = new LambdaQueryWrapper<>();
        query.eq(SysRolePO::getRoleCode, roleCode);
        SysRolePO role = sysRoleMapper.selectOne(query);
        assertNotNull(role);
        SysUserPO user = new SysUserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PWD));
        user.setStatus(1);
        user.setDeleted(0);
        sysUserMapper.insert(user);
        SysUserRolePO userRole = new SysUserRolePO();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);
        createdUserIds.add(user.getId());
        return user.getId();
    }

    private Long createGrid(String name, Long userId) {
        SysGridPO grid = new SysGridPO();
        grid.setGridName(name);
        grid.setGridWorkerId(userId);
        grid.setStatus(1);
        grid.setDeleted(0);
        sysGridMapper.insert(grid);
        return grid.getId();
    }

    private Long seedPlan(String name, Long createBy, String category, String trainingType,
                          String status, int enrolledCount) {
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setPlanName(planPrefix + name);
        plan.setCategory(category);
        plan.setTrainingType(trainingType);
        plan.setStartDate(START);
        plan.setEndDate(END);
        plan.setCapacity(20);
        plan.setEnrolledCount(enrolledCount);
        plan.setOrganizer("计划测试主办单位");
        plan.setStatus(status);
        plan.setRemark("计划测试备注");
        plan.setDeleted(0);
        plan.setCreateBy(createBy);
        trainingPlanMapper.insert(plan);
        return plan.getId();
    }

    private Map<String, Object> saveBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planName", planPrefix + "新增或更新计划");
        body.put("startDate", START.toString());
        body.put("endDate", END.toString());
        body.put("capacity", 20);
        return body;
    }

    private String login(String username) throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername(username);
        dto.setPassword(PWD);
        JsonNode response = request("POST", "/api/auth/login", dto, null);
        assertSuccess(response);
        return response.get("data").get("token").asText();
    }

    private String query(String extra) {
        return "planName=" + enc(planPrefix) + extra;
    }

    private JsonNode pageData(String query, String token) throws Exception {
        JsonNode response = request("GET", API + "/page?" + query, null, token);
        assertSuccess(response);
        return response.get("data");
    }

    private List<Long> idsOf(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode record : page.get("records")) {
            ids.add(record.get("id").asLong());
        }
        return ids;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void assertSuccess(JsonNode response) {
        assertEquals(200, response.get("code").asInt(), response.toString());
    }

    private JsonNode request(String method, String path, Object body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + path)
                .toURL().openConnection();
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
            String response = new String((status >= 400 ? connection.getErrorStream() : connection.getInputStream())
                    .readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(response);
        } finally {
            connection.disconnect();
        }
    }
}

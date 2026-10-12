package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.po.TrainingDemandPO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.mapper.TrainingDemandMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingDemandService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 培训需求接口、持久化及网格权限集成测试，仅使用 H2 测试库。 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingDemandTest {

    private static final String API = "/api/training-demand";
    private static final String PWD = "Test@123456";
    private static final String JOB_TYPE = "就业技能";
    private static final String REHAB_TYPE = "康复训练";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrainingDemandMapper trainingDemandMapper;
    @Autowired
    private DisabledPersonMapper disabledPersonMapper;
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
    private Long businessUserId;
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private Long personAId;
    private Long personBId;
    private Long pendingAId;
    private Long arrangedAId;
    private Long cancelledAId;
    private Long pendingBId;

    @BeforeAll
    void setupUsers() throws Exception {
        createUser("td_test_admin", "ADMIN");
        businessUserId = createUser("td_test_business_a", "BUSINESS");
        Long businessBId = createUser("td_test_business_b", "BUSINESS");
        createUser("td_test_no_grid", "BUSINESS");
        Long multiGridUserId = createUser("td_test_multi_grid", "BUSINESS");
        Long workerId = createUser("td_test_worker", "GRID_WORKER");
        gridAId = createGrid("培训需求测试网格A", businessUserId);
        gridBId = createGrid("培训需求测试网格B", businessBId);
        createGrid("培训需求测试多网格1", multiGridUserId);
        createGrid("培训需求测试多网格2", multiGridUserId);
        createGrid("培训需求测试非业务角色网格", workerId);
        adminToken = login("td_test_admin");
        businessToken = login("td_test_business_a");
        noGridToken = login("td_test_no_grid");
        multiGridToken = login("td_test_multi_grid");
        workerToken = login("td_test_worker");
    }

    @BeforeEach
    void setupDemands() {
        // 各用例使用独立人员和需求，分页及更新不依赖测试执行顺序。
        personAId = createPerson("培训需求测试人员A", gridAId);
        personBId = createPerson("培训需求测试人员B", gridBId);
        pendingAId = seedDemand(personAId, JOB_TYPE, TrainingDemandService.STATUS_PENDING);
        arrangedAId = seedDemand(personAId, REHAB_TYPE, TrainingDemandService.STATUS_ARRANGED);
        cancelledAId = seedDemand(personAId, JOB_TYPE, TrainingDemandService.STATUS_CANCELLED);
        pendingBId = seedDemand(personBId, JOB_TYPE, TrainingDemandService.STATUS_PENDING);
    }

    @AfterAll
    void cleanupTokens() {
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("新增培训需求成功，默认待处理并记录创建人")
    void createDemand() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("demandType", JOB_TYPE);
        body.put("demandDesc", "学习计算机操作");
        body.put("remark", "优先安排周末培训");
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);

        TrainingDemandPO po = trainingDemandMapper.selectById(response.get("data").asLong());
        assertNotNull(po);
        assertEquals(personAId, po.getPersonId());
        assertEquals(JOB_TYPE, po.getDemandType());
        assertEquals("学习计算机操作", po.getDemandDesc());
        assertEquals("优先安排周末培训", po.getRemark());
        assertEquals(TrainingDemandService.STATUS_PENDING, po.getStatus());
        assertEquals(0, po.getDeleted());
        assertEquals(businessUserId, po.getCreateBy());
        assertNotNull(po.getCreateTime());
        assertNotNull(po.getUpdateTime());
    }

    @Test
    @DisplayName("按人员分页，返回正确总数、页数和不同页记录")
    void pageDemands() throws Exception {
        JsonNode first = pageData("personId=" + personAId + "&current=1&size=1", businessToken);
        JsonNode second = pageData("personId=" + personAId + "&current=2&size=1", businessToken);
        assertEquals(3, first.get("total").asLong());
        assertEquals(3, first.get("pages").asLong());
        assertEquals(1, first.get("size").asLong());
        assertEquals(1, first.get("records").size());
        assertEquals(2, second.get("current").asLong());
        assertEquals(cancelledAId.longValue(), first.get("records").get(0).get("id").asLong());
        assertEquals(arrangedAId.longValue(), second.get("records").get(0).get("id").asLong());
        assertEquals(personAId.longValue(), first.get("records").get(0).get("personId").asLong());
    }

    @Test
    @DisplayName("人员、需求类型及状态筛选同时生效")
    void pageFiltersDemandTypeAndStatus() throws Exception {
        JsonNode data = pageData("personId=" + personAId + "&demandType=" + enc(JOB_TYPE) + "&status=0", adminToken);
        assertEquals(1, data.get("total").asLong());
        assertEquals(List.of(pendingAId), idsOf(data));
    }

    @Test
    @DisplayName("查询培训需求详情，不暴露逻辑删除字段")
    void detailDemand() throws Exception {
        JsonNode response = request("GET", API + "/" + pendingAId, null, businessToken);
        assertSuccess(response);
        JsonNode data = response.get("data");
        assertEquals(pendingAId.longValue(), data.get("id").asLong());
        assertEquals(personAId.longValue(), data.get("personId").asLong());
        assertEquals(JOB_TYPE, data.get("demandType").asText());
        assertEquals(TrainingDemandService.STATUS_PENDING, data.get("status").asText());
        assertFalse(data.has("deleted"));
    }

    @Test
    @DisplayName("修改培训需求成功，未传字段保留，记录更新人")
    void updateDemand() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("demandDesc", "安排职业技能培训");
        body.put("status", TrainingDemandService.STATUS_ARRANGED);
        assertSuccess(request("PUT", API + "/" + pendingAId, body, businessToken));

        TrainingDemandPO po = trainingDemandMapper.selectById(pendingAId);
        assertEquals("安排职业技能培训", po.getDemandDesc());
        assertEquals(TrainingDemandService.STATUS_ARRANGED, po.getStatus());
        assertEquals(JOB_TYPE, po.getDemandType());
        assertEquals("需求测试备注", po.getRemark());
        assertEquals(personAId, po.getPersonId());
        assertEquals(businessUserId, po.getUpdateBy());
    }

    @Test
    @DisplayName("删除为逻辑删除，分页和详情排除已删除需求")
    void deleteDemandLogically() throws Exception {
        assertSuccess(request("DELETE", API + "/" + pendingAId, null, businessToken));
        assertNull(trainingDemandMapper.selectById(pendingAId));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_demand WHERE id = ?", Integer.class, pendingAId));
        JsonNode page = pageData("personId=" + personAId, businessToken);
        assertEquals(2, page.get("total").asLong());
        assertFalse(idsOf(page).contains(pendingAId));
        assertEquals(404, request("GET", API + "/" + pendingAId, null, adminToken).get("code").asInt());
        assertNotNull(disabledPersonMapper.selectById(personAId));
    }

    @Test
    @DisplayName("ADMIN 可跨网格查询、新增、修改和删除培训需求")
    void adminCanOperateOtherGrid() throws Exception {
        assertSuccess(request("GET", API + "/" + pendingBId, null, adminToken));
        assertEquals(List.of(pendingBId), idsOf(pageData("personId=" + personBId, adminToken)));
        Map<String, Object> body = saveBody(personBId);
        body.put("demandDesc", "管理员新增跨网格需求");
        JsonNode create = request("POST", API, body, adminToken);
        assertSuccess(create);
        Long newId = create.get("data").asLong();
        body.put("status", TrainingDemandService.STATUS_CANCELLED);
        assertSuccess(request("PUT", API + "/" + newId, body, adminToken));
        assertEquals(TrainingDemandService.STATUS_CANCELLED, trainingDemandMapper.selectById(newId).getStatus());
        assertSuccess(request("DELETE", API + "/" + newId, null, adminToken));
        assertNull(trainingDemandMapper.selectById(newId));
    }

    @Test
    @DisplayName("BUSINESS 分页只返回自己网格人员的培训需求")
    void businessPageIsScopedToGrid() throws Exception {
        JsonNode page = pageData("size=100", businessToken);
        assertTrue(idsOf(page).contains(pendingAId));
        assertFalse(idsOf(page).contains(pendingBId));
        for (JsonNode record : page.get("records")) {
            DisabledPersonPO person = disabledPersonMapper.selectById(record.get("personId").asLong());
            assertNotNull(person);
            assertEquals(gridAId, person.getGridId());
        }
    }

    @Test
    @DisplayName("BUSINESS 伪造人员 ID 无法跨网格查询或写入需求")
    void businessCannotOperateOtherGrid() throws Exception {
        assertEquals(403, request("GET", API + "/page?personId=" + personBId, null, businessToken)
                .get("code").asInt());
        assertEquals(403, request("GET", API + "/" + pendingBId, null, businessToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(personBId), businessToken).get("code").asInt());

        Map<String, Object> forged = saveBody(personAId);
        forged.put("demandDesc", "越权修改");
        assertEquals(403, request("PUT", API + "/" + pendingBId, forged, businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + pendingBId, null, businessToken).get("code").asInt());
        TrainingDemandPO po = trainingDemandMapper.selectById(pendingBId);
        assertNotNull(po);
        assertEquals(personBId, po.getPersonId());
        assertEquals("需求测试描述", po.getDemandDesc());
        assertEquals(TrainingDemandService.STATUS_PENDING, po.getStatus());
        assertEquals(1, pageData("personId=" + personBId, adminToken).get("total").asLong());
    }

    @Test
    @DisplayName("修改培训需求不能更换所属人员")
    void cannotChangeDemandOwner() throws Exception {
        JsonNode response = request("PUT", API + "/" + pendingAId, saveBody(personBId), adminToken);
        assertEquals(400, response.get("code").asInt());
        assertEquals(personAId, trainingDemandMapper.selectById(pendingAId).getPersonId());
    }

    @Test
    @DisplayName("不存在的培训需求不能查询、修改或删除")
    void missingDemandIsRejected() throws Exception {
        String missingPath = API + "/" + Long.MAX_VALUE;
        assertEquals(404, request("GET", missingPath, null, adminToken).get("code").asInt());
        assertEquals(404, request("PUT", missingPath, saveBody(personAId), adminToken).get("code").asInt());
        assertEquals(404, request("DELETE", missingPath, null, adminToken).get("code").asInt());
        JsonNode missing = request("GET", missingPath, null, businessToken);
        JsonNode forbidden = request("GET", API + "/" + pendingBId, null, businessToken);
        assertEquals(403, missing.get("code").asInt());
        assertEquals(missing.get("message").asText(), forbidden.get("message").asText());
    }

    @Test
    @DisplayName("人员不存在或已删除时，不能新增培训需求")
    void createRequiresExistingPerson() throws Exception {
        long before = trainingDemandMapper.selectCount(null);
        assertEquals(404, request("POST", API, saveBody(Long.MAX_VALUE), adminToken).get("code").asInt());
        disabledPersonMapper.deleteById(personAId);
        assertEquals(404, request("POST", API, saveBody(personAId), adminToken).get("code").asInt());
        assertEquals(before, trainingDemandMapper.selectCount(null).longValue());
        assertFalse(idsOf(pageData("size=100", adminToken)).contains(pendingAId));
        assertEquals(404, request("GET", API + "/" + pendingAId, null, adminToken).get("code").asInt());
    }

    @Test
    @DisplayName("无网格账号返回空分页，多网格账号拒绝访问")
    void missingOrMultipleGridsAreRestricted() throws Exception {
        assertEquals(0, pageData("size=100", noGridToken).get("total").asLong());
        assertEquals(403, request("POST", API, saveBody(personAId), noGridToken).get("code").asInt());
        JsonNode response = request("GET", API + "/page", null, multiGridToken);
        assertEquals(403, response.get("code").asInt());
        assertEquals(GridScopeService.MULTI_GRID_MESSAGE, response.get("message").asText());
    }

    @Test
    @DisplayName("未登录及无 ADMIN/BUSINESS 角色时拒绝访问")
    void authenticationAndRolesAreRequired() throws Exception {
        assertEquals(401, request("GET", API + "/page", null, null).get("code").asInt());
        assertEquals(403, request("GET", API + "/page", null, workerToken).get("code").asInt());
    }

    @Test
    @DisplayName("非法状态、缺少人员 ID 及超长描述被校验拒绝")
    void invalidInputIsRejected() throws Exception {
        Map<String, Object> invalidStatus = saveBody(personAId);
        invalidStatus.put("status", "3");
        assertEquals(400, request("POST", API, invalidStatus, businessToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?status=3", null, adminToken).get("code").asInt());
        assertEquals(400, request("POST", API, Map.of("demandDesc", "缺少人员"), businessToken)
                .get("code").asInt());
        Map<String, Object> tooLong = saveBody(personAId);
        tooLong.put("demandDesc", "长".repeat(501));
        assertEquals(400, request("POST", API, tooLong, businessToken).get("code").asInt());
        assertEquals(3, pageData("personId=" + personAId, businessToken).get("total").asLong());
    }

    private Long createUser(String username, String roleCode) {
        LambdaQueryWrapper<SysRolePO> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(SysRolePO::getRoleCode, roleCode);
        SysRolePO role = sysRoleMapper.selectOne(roleQuery);
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

    private Long createPerson(String name, Long gridId) {
        DisabledPersonPO person = new DisabledPersonPO();
        person.setName(name);
        person.setGridId(gridId);
        person.setStatus(1);
        person.setDeleted(0);
        disabledPersonMapper.insert(person);
        return person.getId();
    }

    private Long seedDemand(Long personId, String demandType, String status) {
        TrainingDemandPO demand = new TrainingDemandPO();
        demand.setPersonId(personId);
        demand.setDemandType(demandType);
        demand.setDemandDesc("需求测试描述");
        demand.setStatus(status);
        demand.setRemark("需求测试备注");
        demand.setDeleted(0);
        trainingDemandMapper.insert(demand);
        return demand.getId();
    }

    private Map<String, Object> saveBody(Long personId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", personId);
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

    private String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
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

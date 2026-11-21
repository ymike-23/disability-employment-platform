package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.service.UserService;
import com.yzr.disability.service.TokenService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 管理员创建业务账号及网格分配集成测试，业务数据仅写入 H2。 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminCreateUserTest {

    private static final String PWD = "Admin@123456";

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysGridMapper sysGridMapper;
    @Autowired
    private DisabledPersonMapper disabledPersonMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private TokenService tokenService;
    private final Map<Long, String> tokens = new ConcurrentHashMap<>();
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long bizUserId;
    private String adminToken;
    private String bizToken;

    @BeforeAll
    void setup() throws Exception {
        stubTokens();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:"));
        }
        createUser("uc_admin", roleIdOf("ADMIN"));
        bizUserId = createUser("uc_biz", roleIdOf("BUSINESS"));
        adminToken = login("uc_admin", PWD);
        bizToken = login("uc_biz", PWD);
    }

    @AfterAll
    void cleanup() {
        // 账号及会话均为隔离测试数据，不连接开发Redis。
        for (Long userId : createdUserIds) {
            tokens.remove(userId);
        }
    }

    @BeforeEach
    void stubTokens() {
        when(tokenService.getToken(anyLong())).thenAnswer(call -> tokens.get(call.getArgument(0)));
        doAnswer(call -> { tokens.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(tokenService).saveToken(anyLong(), anyString());
        doAnswer(call -> { tokens.remove(call.getArgument(0)); return null; }).when(tokenService).deleteToken(anyLong());
    }

    @Test
    @DisplayName("ADMIN 创建账号、绑定 BUSINESS 角色并分配真实网格")
    void adminCreateSuccess() throws Exception {
        Long gridId = createGrid("UC-创建成功", null, 1, 0);
        String resp = post("/api/admin/addusers", createBody("uc_newbiz01", "Biz@123456", gridId), adminToken);
        assertEquals(200, codeOf(resp));
        JsonNode data = objectMapper.readTree(resp).get("data");
        Long newUserId = data.get("id").asLong();
        createdUserIds.add(newUserId);
        assertEquals("uc_newbiz01", data.get("username").asText());
        assertEquals("BUSINESS", data.get("roleCode").asText());
        assertEquals(gridId.longValue(), data.get("gridId").asLong());
        assertEquals("UC-创建成功", data.get("gridName").asText());
        assertFalse(resp.contains("Biz@123456"));
        SysUserPO user = sysUserMapper.selectById(newUserId);
        assertNotNull(user);
        assertEquals(1, user.getStatus());
        assertNotEquals("Biz@123456", user.getPassword());
        assertTrue(passwordEncoder.matches("Biz@123456", user.getPassword()));
        List<SysUserRolePO> roles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePO>()
                .eq(SysUserRolePO::getUserId, newUserId));
        assertEquals(1, roles.size());
        assertEquals("BUSINESS", sysRoleMapper.selectById(roles.getFirst().getRoleId()).getRoleCode());
        SysGridPO grid = sysGridMapper.selectById(gridId);
        assertEquals(newUserId, grid.getGridWorkerId());
        assertEquals("uc_newbiz01", grid.getGridWorkerName());
    }

    @Test
    @DisplayName("新账号登录后立即受真实网格权限限制，伪造 gridId 不扩权")
    void createdUserCanLogin() throws Exception {
        Long ownGrid = createGrid("UC-权限本网格", null, 1, 0);
        Long otherGrid = createGrid("UC-权限其他网格", bizUserId, 1, 0);
        Long ownPerson = createPerson("UC-SCOPE-own", "110101199001016001", ownGrid);
        Long otherPerson = createPerson("UC-SCOPE-other", "110101199001016002", otherGrid);
        String resp = post("/api/admin/addusers", createBody("uc_newbiz02", "Biz@654321", ownGrid), adminToken);
        assertEquals(200, codeOf(resp));
        createdUserIds.add(objectMapper.readTree(resp).get("data").get("id").asLong());
        String newToken = login("uc_newbiz02", "Biz@654321");
        assertEquals(200, codeOf(get("/api/disabled-person/" + ownPerson, newToken)));
        assertEquals(403, codeOf(get("/api/disabled-person/" + otherPerson, newToken)));
        String page = get("/api/disabled-person/page?current=1&size=50&name=UC-SCOPE", newToken);
        assertEquals(200, codeOf(page));
        JsonNode data = objectMapper.readTree(page).get("data");
        assertEquals(1, data.get("total").asInt());
        assertEquals(ownPerson.longValue(), data.get("records").get(0).get("id").asLong());
        String forged = get("/api/disabled-person/page?name=UC-SCOPE&gridId=" + otherGrid, newToken);
        if (codeOf(forged) == 200) {
            for (JsonNode record : objectMapper.readTree(forged).get("data").get("records")) {
                assertNotEquals(otherPerson.longValue(), record.get("id").asLong());
            }
        } else {
            assertEquals(403, codeOf(forged));
        }
        assertEquals(objectMapper.readTree(resp).get("data").get("id").asLong(),
                sysGridMapper.selectById(ownGrid).getGridWorkerId().longValue());
    }

    @Test
    @DisplayName("BUSINESS 不能创建账号或借请求参数更改自身网格")
    void businessCannotCreateUser() throws Exception {
        Long gridId = createGrid("UC-业务越权", null, 1, 0);
        assertEquals(403, codeOf(post("/api/admin/addusers", createBody("uc_biz_try", PWD, gridId), bizToken)));
        assertNoUser("uc_biz_try");
        assertNull(sysGridMapper.selectById(gridId).getGridWorkerId());
    }

    @Test
    @DisplayName("重复用户名返回 409，网格不被占用")
    void duplicateUsernameRejected() throws Exception {
        Long gridId = createGrid("UC-重复用户名", null, 1, 0);
        String resp = post("/api/admin/addusers", createBody("uc_admin", PWD, gridId), adminToken);
        assertEquals(409, codeOf(resp));
        assertEquals("用户名已存在", messageOf(resp));
        assertNull(sysGridMapper.selectById(gridId).getGridWorkerId());
    }

    @Test
    @DisplayName("BUSINESS 角色停用返回 403，不创建账号或分配网格")
    void disabledBusinessRoleReturns403() throws Exception {
        Long gridId = createGrid("UC-角色停用", null, 1, 0);
        SysRolePO role = sysRoleMapper.selectById(roleIdOf("BUSINESS"));
        Integer originalStatus = role.getStatus();
        try {
            role.setStatus(0);
            assertEquals(1, sysRoleMapper.updateById(role));
            String resp = post("/api/admin/addusers", createBody("uc_role_disabled", PWD, gridId), adminToken);
            assertEquals(403, codeOf(resp));
            assertEquals("业务人员角色未配置或已停用，请联系管理员处理", messageOf(resp));
            assertNoUser("uc_role_disabled");
            assertNull(sysGridMapper.selectById(gridId).getGridWorkerId());
        } finally {
            role.setStatus(originalStatus);
            sysRoleMapper.updateById(role);
        }
    }

    @Test
    @DisplayName("空用户名或过短密码返回 400")
    void invalidParamReturns400() throws Exception {
        Long gridId = createGrid("UC-参数校验", null, 1, 0);
        assertEquals(400, codeOf(post("/api/admin/addusers", createBody("", PWD, gridId), adminToken)));
        assertEquals(400, codeOf(post("/api/admin/addusers", createBody("uc_short", "123", gridId), adminToken)));
        assertNoUser("uc_short");
        assertNull(sysGridMapper.selectById(gridId).getGridWorkerId());
    }

    @Test
    @DisplayName("未登录不能创建账号")
    void anonymousReturns401() throws Exception {
        Long gridId = createGrid("UC-匿名", null, 1, 0);
        assertEquals(401, codeOf(post("/api/admin/addusers", createBody("uc_anon", PWD, gridId), null)));
        assertNoUser("uc_anon");
    }

    @Test
    @DisplayName("ADMIN 仅获得启用、未删除、未分配的网格选项")
    void adminListsAssignableGrids() throws Exception {
        Long freeId = createGrid("UC-可分配", null, 1, 0);
        Long disabledId = createGrid("UC-停用", null, 0, 0);
        Long deletedId = createGrid("UC-已删除", null, 1, 1);
        Long occupiedId = createGrid("UC-已分配", bizUserId, 1, 0);
        String resp = get("/api/admin/grids", adminToken);
        assertEquals(200, codeOf(resp));
        List<Long> ids = new ArrayList<>();
        for (JsonNode option : objectMapper.readTree(resp).get("data")) {
            Long id = option.get("id").asLong();
            ids.add(id);
            SysGridPO grid = sysGridMapper.selectById(id);
            assertNotNull(grid);
            assertEquals(1, grid.getStatus());
            assertNull(grid.getGridWorkerId());
            assertEquals(grid.getGridName(), option.get("gridName").asText());
            assertFalse(option.has("deleted"));
            assertFalse(option.has("gridWorkerId"));
        }
        assertTrue(ids.contains(freeId));
        assertFalse(ids.contains(disabledId));
        assertFalse(ids.contains(deletedId));
        assertFalse(ids.contains(occupiedId));
    }

    @Test
    @DisplayName("网格选择接口仅 ADMIN 可调用")
    void gridOptionsRejectBusinessAndAnonymous() throws Exception {
        assertEquals(403, codeOf(get("/api/admin/grids", bizToken)));
        assertEquals(401, codeOf(get("/api/admin/grids", null)));
    }

    @Test
    @DisplayName("网格必填且为正 ID，旧 gridName 参数不能替代 ID")
    void missingOrNonPositiveGridRejected() throws Exception {
        assertEquals(400, codeOf(post("/api/admin/addusers",
                "{\"username\":\"uc_missing_grid\",\"password\":\"Admin@123456\",\"gridName\":\"任意网格\"}", adminToken)));
        assertEquals(400, codeOf(post("/api/admin/addusers", createBody("uc_grid_zero", PWD, 0L), adminToken)));
        assertEquals(400, codeOf(post("/api/admin/addusers", createBody("uc_grid_negative", PWD, -1L), adminToken)));
        assertNoUser("uc_missing_grid");
        assertNoUser("uc_grid_zero");
        assertNoUser("uc_grid_negative");
    }

    @Test
    @DisplayName("不存在、停用及已删除网格均不能分配")
    void invalidGridRejected() throws Exception {
        Long disabledId = createGrid("UC-不可用停用", null, 0, 0);
        Long deletedId = createGrid("UC-不可用删除", null, 1, 1);
        for (Long gridId : List.of(Long.MAX_VALUE, disabledId, deletedId)) {
            String username = "uc_invalid_" + gridId;
            assertEquals(400, codeOf(post("/api/admin/addusers", createBody(username, PWD, gridId), adminToken)));
            assertNoUser(username);
        }
    }

    @Test
    @DisplayName("已分配网格返回 409，原有业务人员关联不变")
    void occupiedGridRejected() throws Exception {
        Long gridId = createGrid("UC-不可覆盖", bizUserId, 1, 0);
        assertEquals(409, codeOf(post("/api/admin/addusers", createBody("uc_occupied", PWD, gridId), adminToken)));
        assertNoUser("uc_occupied");
        assertEquals(bizUserId, sysGridMapper.selectById(gridId).getGridWorkerId());
    }

    @Test
    @DisplayName("Service 层同样拒绝 BUSINESS 创建账号和查询可分配网格")
    void serviceRejectsBusinessDirectCall() {
        Long gridId = createGrid("UC-Service越权", null, 1, 0);
        var previous = SecurityContextHolder.getContext();
        try {
            var context = SecurityContextHolder.createEmptyContext();
            LoginUser principal = new LoginUser(bizUserId, "uc_biz", "", true,
                    List.of(new SimpleGrantedAuthority("ROLE_BUSINESS")));
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            CreateUserDTO dto = new CreateUserDTO();
            dto.setUsername("uc_service_forbidden");
            dto.setPassword(PWD);
            dto.setGridId(gridId);
            assertEquals(403, assertThrows(BusinessException.class, () -> userService.createBusinessUser(dto)).getCode());
            assertEquals(403, assertThrows(BusinessException.class, () -> userService.listAssignableGrids()).getCode());
        } finally {
            SecurityContextHolder.setContext(previous);
        }
        assertNoUser("uc_service_forbidden");
        assertNull(sysGridMapper.selectById(gridId).getGridWorkerId());
    }

    @Test
    @DisplayName("网格写入失败时账号、角色和网格分配全部回滚")
    void gridWriteFailureRollsBackUserAndRole() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:"), "故障注入仅允许 H2 测试库");
        }
        Long gridId = createGrid("UC-事务回滚", null, 1, 0);
        Long rolesBefore = sysUserRoleMapper.selectCount(null);
        // 在事务最后的网格写入处注入失败，确认已插入的账号和角色也会回滚。
        jdbcTemplate.execute("ALTER TABLE sys_grid ADD CONSTRAINT uc_assignment_rollback "
                + "CHECK (grid_worker_name IS NULL OR grid_worker_name <> 'uc_rollback')");
        try {
            String resp = post("/api/admin/addusers", createBody("uc_rollback", PWD, gridId), adminToken);
            assertEquals(500, codeOf(resp));
            assertNoUser("uc_rollback");
            assertEquals(rolesBefore, sysUserRoleMapper.selectCount(null));
            SysGridPO grid = sysGridMapper.selectById(gridId);
            assertNull(grid.getGridWorkerId());
            assertNull(grid.getGridWorkerName());
        } finally {
            jdbcTemplate.execute("ALTER TABLE sys_grid DROP CONSTRAINT uc_assignment_rollback");
        }
    }

    @Test
    @DisplayName("并发分配同一网格只有一个成功，不遗留失败账号")
    void concurrentGridAssignmentHasOneWinner() throws Exception {
        Long gridId = createGrid("UC-并发分配", null, 1, 0);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return post("/api/admin/addusers", createBody("uc_race_a", PWD, gridId), adminToken);
            });
            var second = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return post("/api/admin/addusers", createBody("uc_race_b", PWD, gridId), adminToken);
            });
            start.countDown();
            String firstResp = first.get(20, TimeUnit.SECONDS);
            String secondResp = second.get(20, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), List.of(codeOf(firstResp), codeOf(secondResp)).stream().sorted().toList());
            String success = codeOf(firstResp) == 200 ? firstResp : secondResp;
            String failedUsername = codeOf(firstResp) == 200 ? "uc_race_b" : "uc_race_a";
            Long userId = objectMapper.readTree(success).get("data").get("id").asLong();
            createdUserIds.add(userId);
            assertEquals(userId, sysGridMapper.selectById(gridId).getGridWorkerId());
            assertNoUser(failedUsername);
            assertEquals(1, sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRolePO>()
                    .eq(SysUserRolePO::getUserId, userId)).intValue());
        } finally {
            executor.shutdownNow();
        }
    }

    private Long roleIdOf(String roleCode) {
        SysRolePO role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRolePO>().eq(SysRolePO::getRoleCode, roleCode));
        assertNotNull(role);
        return role.getId();
    }

    private Long createUser(String username, Long roleId) {
        SysUserPO user = new SysUserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PWD));
        user.setStatus(1);
        user.setDeleted(0);
        sysUserMapper.insert(user);
        SysUserRolePO ur = new SysUserRolePO();
        ur.setUserId(user.getId());
        ur.setRoleId(roleId);
        sysUserRoleMapper.insert(ur);
        createdUserIds.add(user.getId());
        return user.getId();
    }

    private Long createGrid(String name, Long workerId, int status, int deleted) {
        SysGridPO grid = new SysGridPO();
        grid.setGridName(name);
        grid.setGridCode("UC");
        grid.setDistrict("1");
        grid.setCommunity("UC测试社区");
        grid.setGridWorkerId(workerId);
        grid.setStatus(status);
        grid.setDeleted(deleted);
        assertEquals(1, sysGridMapper.insert(grid));
        return grid.getId();
    }

    private Long createPerson(String name, String idCard, Long gridId) {
        DisabledPersonPO person = new DisabledPersonPO();
        person.setName(name);
        person.setIdCard(idCard);
        person.setGridId(gridId);
        person.setStatus(1);
        person.setDeleted(0);
        assertEquals(1, disabledPersonMapper.insert(person));
        return person.getId();
    }

    private void assertNoUser(String username) {
        assertEquals(0L, sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserPO>()
                .eq(SysUserPO::getUsername, username)).longValue());
    }

    private String createBody(String username, String password, Long gridId) throws Exception {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setGridId(gridId);
        return objectMapper.writeValueAsString(dto);
    }

    private String login(String username, String password) throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(username);
        req.setPassword(password);
        String resp = post("/api/auth/login", objectMapper.writeValueAsString(req), null);
        assertEquals(200, codeOf(resp), "登录应成功: " + username);
        return objectMapper.readTree(resp).get("data").get("token").asText();
    }

    private String request(String method, String path, String body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = conn.getResponseCode();
        try (var input = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private String get(String path, String token) throws Exception {
        return request("GET", path, null, token);
    }

    private String post(String path, String body, String token) throws Exception {
        return request("POST", path, body, token);
    }

    private int codeOf(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asInt();
    }

    private String messageOf(String json) throws Exception {
        return objectMapper.readTree(json).get("message").asText();
    }
}

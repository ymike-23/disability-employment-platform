package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.service.GridScopeService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 残疾人档案数据权限集成测试。
 *
 * <p>所有测试数据写入 H2 内存库（src/test/resources/application.yml），
 * 不连接开发库 disability_db。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisabledPersonPermissionTest {

    private static final String PWD = "Test@123456";

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private GridScopeService gridScopeService;

    /** 各账号 ID，用于测试后清理 Redis Token */
    private final List<Long> createdUserIds = new ArrayList<>();

    private Long grid1Id, grid2Id;
    private Long gridWorkerUserId;
    private Long p1Id, p2Id, pNoGridId, pDeletedId;
    private String adminToken, biz1Token, biz2Token, noGridToken, multiToken;

    @BeforeAll
    void setup() throws Exception {
        Long adminRoleId = roleIdOf("ADMIN");
        Long bizRoleId = roleIdOf("BUSINESS");
        Long gridWorkerRoleId = roleIdOf("GRID_WORKER");

        // 1. 用户
        Long adminId = createUser("dp_admin", adminRoleId);
        Long biz1Id = createUser("dp_biz1", bizRoleId);
        Long biz2Id = createUser("dp_biz2", bizRoleId);
        Long noGridId = createUser("dp_nogrid", bizRoleId);
        Long multiId = createUser("dp_multi", bizRoleId);
        // 仅 GRID_WORKER 角色（不参与授权），且故意为其绑定网格，
        // 用于验证：即使存在网格关联，无 ADMIN/BUSINESS 角色仍不得放行
        gridWorkerUserId = createUser("dp_gridworker", gridWorkerRoleId);

        // 2. 网格（dp_multi 绑定两个网格，用于验证多网格拒绝）
        grid1Id = createGrid("测试网格A", biz1Id);
        grid2Id = createGrid("测试网格B", biz2Id);
        createGrid("测试网格C", multiId);
        createGrid("测试网格D", multiId);
        // 为 GRID_WORKER 账号也绑定网格（最危险的绕过场景）
        createGrid("测试网格E", gridWorkerUserId);

        // 3. 档案
        p1Id = createPerson("档案A1", "110101199001011111", grid1Id);
        p2Id = createPerson("档案B1", "110101199001012222", grid2Id);
        pNoGridId = createPerson("档案无网格", "110101199001013333", null);
        pDeletedId = createPerson("档案已删除", "110101199001014444", grid1Id);
        // 逻辑删除最后一条
        disabledPersonMapper.deleteById(pDeletedId);

        // 4. 登录获取 Token
        adminToken = login("dp_admin");
        biz1Token = login("dp_biz1");
        biz2Token = login("dp_biz2");
        noGridToken = login("dp_nogrid");
        multiToken = login("dp_multi");
    }

    @AfterAll
    void cleanup() {
        // H2 随 JVM 销毁无需清理；仅清理写入 Redis 的测试 Token
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("✅ ADMIN 可查询全部档案（含无网格档案，不含软删除）")
    void adminSeesAll() throws Exception {
        JsonNode data = pageData(adminToken);
        List<Long> ids = idsOf(data);

        assertTrue(ids.contains(p1Id), "ADMIN 应看到网格A档案");
        assertTrue(ids.contains(p2Id), "ADMIN 应看到网格B档案");
        assertTrue(ids.contains(pNoGridId), "ADMIN 应看到无网格档案");
        assertFalse(ids.contains(pDeletedId), "软删除档案不应出现");
    }

    @Test
    @DisplayName("✅ BUSINESS 只能查询自己网格的档案")
    void businessSeesOnlyOwnGrid() throws Exception {
        List<Long> ids = idsOf(pageData(biz1Token));

        assertTrue(ids.contains(p1Id), "业务人员应看到本网格档案");
        assertFalse(ids.contains(p2Id), "不应看到其他网格档案");
        assertFalse(ids.contains(pNoGridId), "不应看到无网格档案");
        assertFalse(ids.contains(pDeletedId), "不应看到软删除档案");
    }

    @Test
    @DisplayName("✅ BUSINESS 无网格时返回空分页")
    void businessWithoutGridReturnsEmpty() throws Exception {
        String resp = get("/api/disabled-person/page?current=1&size=50", noGridToken);
        assertEquals(200, codeOf(resp), "无网格应正常返回而非报错");

        JsonNode data = objectMapper.readTree(resp).get("data");
        assertEquals(0, data.get("total").asInt(), "总数应为 0");
        assertEquals(0, data.get("records").size(), "记录应为空");

        // 详情、写操作应被拒绝
        assertEquals(403, codeOf(get("/api/disabled-person/" + p1Id, noGridToken)));
    }

    @Test
    @DisplayName("✅ BUSINESS 关联多个网格时拒绝访问（不扩大权限）")
    void businessWithMultipleGridsRejected() throws Exception {
        String resp = get("/api/disabled-person/page?current=1&size=50", multiToken);

        assertEquals(403, codeOf(resp), "多网格应拒绝访问");
        assertEquals(GridScopeService.MULTI_GRID_MESSAGE, messageOf(resp), "应返回统一提示");
        assertFalse(resp.contains("grid"), "不应泄露网格内部信息");
    }

    @Test
    @DisplayName("🔒 BUSINESS 越权查看其他网格档案 → 403")
    void businessCannotViewOtherGrid() throws Exception {
        String resp = get("/api/disabled-person/" + p2Id, biz1Token);
        assertEquals(403, codeOf(resp), "越权查看应返回 403");
    }

    @Test
    @DisplayName("🔒 BUSINESS 越权修改其他网格档案 → 403 且数据未被修改")
    void businessCannotUpdateOtherGrid() throws Exception {
        String body = "{\"name\":\"越权改名\",\"gender\":\"男\"}";
        String resp = put("/api/disabled-person/" + p2Id, body, biz1Token);
        assertEquals(403, codeOf(resp), "越权修改应返回 403");

        DisabledPersonPO po = disabledPersonMapper.selectById(p2Id);
        assertEquals("档案B1", po.getName(), "数据不应被修改");
    }

    @Test
    @DisplayName("🔒 BUSINESS 越权删除其他网格档案 → 403 且数据未被删除")
    void businessCannotDeleteOtherGrid() throws Exception {
        String resp = delete("/api/disabled-person/" + p2Id, biz1Token);
        assertEquals(403, codeOf(resp), "越权删除应返回 403");

        DisabledPersonPO po = disabledPersonMapper.selectById(p2Id);
        assertNotNull(po, "数据不应被删除");
    }

    @Test
    @DisplayName("🔒 BUSINESS 伪造 gridId 新增档案 → 后端强制归属自己网格")
    void businessCannotForgeGridId() throws Exception {
        String body = "{\"name\":\"伪造网格档案\",\"idCard\":\"110101199001015555\",\"gridId\":" + grid2Id + "}";
        String resp = post("/api/disabled-person", body, biz1Token);
        assertEquals(200, codeOf(resp), "新增应成功");

        Long newId = objectMapper.readTree(resp).get("data").asLong();
        DisabledPersonPO po = disabledPersonMapper.selectById(newId);
        assertEquals(grid1Id, po.getGridId(), "gridId 应由后端解析为本人的网格，而非前端伪造值");
        assertNotEquals(grid2Id, po.getGridId(), "不得采用前端伪造的 gridId");
        assertEquals(1, po.getStatus(), "status 应默认为 1");
    }

    @Test
    @DisplayName("🔒 身份证号重复 → 拒绝新增")
    void idCardDuplicateRejected() throws Exception {
        String body = "{\"name\":\"重复身份证\",\"idCard\":\"110101199001011111\"}";
        String resp = post("/api/disabled-person", body, biz1Token);

        assertEquals(500, codeOf(resp), "身份证重复应被拒绝");
        assertEquals("身份证号已存在", messageOf(resp));
    }

    @Test
    @DisplayName("🔒 未登录访问 → 401")
    void anonymousAccessReturns401() throws Exception {
        assertEquals(401, getRaw("/api/disabled-person/page", null), "未登录访问列表应 401");
        assertEquals(401, getRaw("/api/disabled-person/" + p1Id, null), "未登录访问详情应 401");
    }

    @Test
    @DisplayName("🔒 非 ADMIN/BUSINESS 角色被网格范围服务拒绝（纵深防御）")
    void nonArchiveRoleRejectedByGridScopeService() {
        // 模拟仅 GRID_WORKER 角色、且已绑定网格的登录用户（最易被误放行的场景）
        LoginUser loginUser = new LoginUser(
                gridWorkerUserId, "dp_gridworker", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_GRID_WORKER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> gridScopeService.isAdmin(),
                    "非 ADMIN/BUSINESS 角色应被网格范围服务拒绝");
            assertEquals(403, ex.getCode(), "应返回 403");
            assertEquals("当前账号无档案访问权限", ex.getMessage(), "应返回统一提示");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("🔒 非 ADMIN/BUSINESS 角色 HTTP 访问档案接口被拦截")
    void nonArchiveRoleRejectedByHttp() throws Exception {
        String token = login("dp_gridworker");
        String resp = get("/api/disabled-person/page?current=1&size=50", token);

        assertEquals(403, codeOf(resp), "GRID_WORKER 角色不应获得档案访问权限");
    }

    @Test
    @DisplayName("🔒 BUSINESS 查询不存在档案与他网格档案 → 错误码和提示一致（防枚举）")
    void businessDetailErrorIsConsistent() throws Exception {
        String notExist = get("/api/disabled-person/99999999", biz1Token);
        String otherGrid = get("/api/disabled-person/" + p2Id, biz1Token);

        assertEquals(403, codeOf(notExist), "不存在档案应返回 403，不得返回 404");
        assertEquals(403, codeOf(otherGrid), "其他网格档案应返回 403");
        assertEquals(codeOf(notExist), codeOf(otherGrid), "两者错误码必须一致");
        assertEquals(messageOf(notExist), messageOf(otherGrid), "两者提示必须一致");
        assertEquals("档案不存在或无权访问", messageOf(notExist), "应返回统一提示");
    }

    // ==================== 辅助方法 ====================

    private Long roleIdOf(String roleCode) {
        LambdaQueryWrapper<SysRolePO> w = new LambdaQueryWrapper<>();
        w.eq(SysRolePO::getRoleCode, roleCode);
        SysRolePO role = sysRoleMapper.selectOne(w);
        assertNotNull(role, "角色应存在: " + roleCode);
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

    private Long createGrid(String gridName, Long workerId) {
        SysGridPO grid = new SysGridPO();
        grid.setGridName(gridName);
        grid.setGridWorkerId(workerId);
        grid.setStatus(1);
        grid.setDeleted(0);
        sysGridMapper.insert(grid);
        return grid.getId();
    }

    private Long createPerson(String name, String idCard, Long gridId) {
        DisabledPersonPO po = new DisabledPersonPO();
        po.setName(name);
        po.setIdCard(idCard);
        po.setGridId(gridId);
        po.setStatus(1);
        po.setDeleted(0);
        disabledPersonMapper.insert(po);
        return po.getId();
    }

    private String login(String username) throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(username);
        req.setPassword(PWD);
        String resp = post("/api/auth/login", objectMapper.writeValueAsString(req), null);
        assertEquals(200, codeOf(resp), "登录应成功: " + username);
        return objectMapper.readTree(resp).get("data").get("token").asText();
    }

    private JsonNode pageData(String token) throws Exception {
        String resp = get("/api/disabled-person/page?current=1&size=50", token);
        assertEquals(200, codeOf(resp), "分页查询应成功");
        return objectMapper.readTree(resp).get("data");
    }

    private List<Long> idsOf(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : data.get("records")) {
            ids.add(node.get("id").asLong());
        }
        return ids;
    }

    private int codeOf(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asInt();
    }

    private String messageOf(String json) throws Exception {
        return objectMapper.readTree(json).get("message").asText();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 统一请求方法，返回响应体 */
    private String request(String method, String path, String body, String token) throws Exception {
        URL u = URI.create(url(path)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setDoOutput(true);
        if (body != null) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = conn.getResponseCode();
        String resp = new String((status >= 400 ? conn.getErrorStream() : conn.getInputStream())
                .readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return resp;
    }

    private String get(String path, String token) throws Exception {
        return request("GET", path, null, token);
    }

    private String post(String path, String body, String token) throws Exception {
        return request("POST", path, body, token);
    }

    private String put(String path, String body, String token) throws Exception {
        return request("PUT", path, body, token);
    }

    private String delete(String path, String token) throws Exception {
        return request("DELETE", path, null, token);
    }

    /** 仅用于 401 场景：直接返回 HTTP 状态码 */
    private int getRaw(String path, String token) throws Exception {
        URL u = URI.create(url(path)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }
}

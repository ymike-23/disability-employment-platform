package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
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
 * 管理员创建业务人员账号测试。
 *
 * <p>测试数据写入 H2 内存库，不连接开发库 disability_db。
 */
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    private String adminToken;
    private String bizToken;

    @BeforeAll
    void setup() throws Exception {
        Long adminRoleId = roleIdOf("ADMIN");
        Long bizRoleId = roleIdOf("BUSINESS");

        createUser("uc_admin", adminRoleId);
        createUser("uc_biz", bizRoleId);

        adminToken = login("uc_admin");
        bizToken = login("uc_biz");
    }

    @AfterAll
    void cleanup() {
        // H2 随 JVM 销毁；仅清理 Redis 中的测试 Token
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("✅ ADMIN 创建业务人员账号成功（BCrypt 存储 + 绑定 BUSINESS 角色）")
    void adminCreateSuccess() throws Exception {
        String body = "{\"username\":\"uc_newbiz01\",\"password\":\"Biz@123456\"}";
        String resp = post("/api/admin/addusers", body, adminToken);

        assertEquals(200, codeOf(resp), "管理员创建账号应成功");

        JsonNode data = objectMapper.readTree(resp).get("data");
        Long newUserId = data.get("id").asLong();
        assertEquals("uc_newbiz01", data.get("username").asText());
        assertEquals("BUSINESS", data.get("roleCode").asText(), "默认角色应为 BUSINESS");
        assertFalse(resp.contains("Biz@123456"), "响应不得包含密码");

        // 数据库校验
        SysUserPO user = sysUserMapper.selectById(newUserId);
        assertNotNull(user, "账号应已创建");
        assertEquals(1, user.getStatus(), "状态应为启用");
        assertNotEquals("Biz@123456", user.getPassword(), "密码不得明文存储");
        assertTrue(user.getPassword().startsWith("$2"), "密码应为 BCrypt 哈希");
        assertTrue(passwordEncoder.matches("Biz@123456", user.getPassword()), "BCrypt 应能匹配初始密码");

        // 角色关联校验
        LambdaQueryWrapper<SysUserRolePO> urw = new LambdaQueryWrapper<>();
        urw.eq(SysUserRolePO::getUserId, newUserId);
        List<SysUserRolePO> userRoles = sysUserRoleMapper.selectList(urw);
        assertEquals(1, userRoles.size(), "应绑定 1 个角色");

        SysRolePO role = sysRoleMapper.selectById(userRoles.get(0).getRoleId());
        assertEquals("BUSINESS", role.getRoleCode(), "绑定的应为业务人员角色");
        assertNotEquals("ADMIN", role.getRoleCode(), "不得创建管理员角色");

        createdUserIds.add(newUserId);
    }

    @Test
    @DisplayName("✅ 新建账号可使用初始密码登录")
    void createdUserCanLogin() throws Exception {
        String body = "{\"username\":\"uc_newbiz02\",\"password\":\"Biz@654321\"}";
        String resp = post("/api/admin/addusers", body, adminToken);
        assertEquals(200, codeOf(resp));

        Long newUserId = objectMapper.readTree(resp).get("data").get("id").asLong();
        createdUserIds.add(newUserId);

        // 新账号登录
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("uc_newbiz02");
        req.setPassword("Biz@654321");
        String loginResp = post("/api/auth/login", objectMapper.writeValueAsString(req), null);
        assertEquals(200, codeOf(loginResp), "新建账号应能登录");
    }

    @Test
    @DisplayName("🔒 BUSINESS 调用创建账号接口 → 403")
    void businessCannotCreateUser() throws Exception {
        String body = "{\"username\":\"uc_biz_try\",\"password\":\"Biz@123456\"}";
        String resp = post("/api/admin/addusers", body, bizToken);

        assertEquals(403, codeOf(resp), "业务人员不应有创建账号权限");
    }

    @Test
    @DisplayName("🔒 用户名重复 → 创建失败")
    void duplicateUsernameRejected() throws Exception {
        String body = "{\"username\":\"uc_admin\",\"password\":\"Biz@123456\"}";
        String resp = post("/api/admin/addusers", body, adminToken);

        assertEquals(500, codeOf(resp), "重复用户名应被拒绝");
        assertEquals("用户名已存在", messageOf(resp));
    }

    @Test
    @DisplayName("🔒 参数异常：用户名为空 / 密码过短 → 400")
    void invalidParamReturns400() throws Exception {
        // 用户名为空
        String emptyName = post("/api/admin/addusers", "{\"username\":\"\",\"password\":\"Biz@123456\"}", adminToken);
        assertEquals(400, codeOf(emptyName), "空用户名应返回 400");

        // 密码少于 6 位
        String shortPwd = post("/api/admin/addusers", "{\"username\":\"uc_short\",\"password\":\"123\"}", adminToken);
        assertEquals(400, codeOf(shortPwd), "密码过短应返回 400");
    }

    @Test
    @DisplayName("🔒 未登录调用 → 401")
    void anonymousReturns401() throws Exception {
        String body = "{\"username\":\"uc_anon\",\"password\":\"Biz@123456\"}";
        assertEquals(401, statusOf(post("/api/admin/addusers", body, null)), "未登录应返回 401");
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

    private String login(String username) throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(username);
        req.setPassword(PWD);
        String resp = post("/api/auth/login", objectMapper.writeValueAsString(req), null);
        assertEquals(200, codeOf(resp), "登录应成功: " + username);
        return objectMapper.readTree(resp).get("data").get("token").asText();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String post(String path, String body, String token) throws Exception {
        URL u = URI.create(url(path)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
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

    private int statusOf(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asInt();
    }

    private int codeOf(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asInt();
    }

    private String messageOf(String json) throws Exception {
        return objectMapper.readTree(json).get("message").asText();
    }
}
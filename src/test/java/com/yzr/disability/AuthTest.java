package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestProtectedController.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_USERNAME = "test_admin";
    private static final String TEST_PASSWORD = "123456";

    private static String jwtToken;

    @BeforeAll
    void setup() {
        // 物理删除（username 有唯一索引，逻辑删除会残留导致重复插入失败）
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);

        SysUserPO user = new SysUserPO();
        user.setUsername(TEST_USERNAME);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);
    }

    // ========== 辅助方法 ==========

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String postJson(String apiPath, String jsonBody) throws Exception {
        URL url = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String body = new String(
                (code >= 400 ? conn.getErrorStream() : conn.getInputStream())
                        .readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return body;
    }

    private int getStatus(String apiPath, String authHeader) throws Exception {
        URL url = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private String getBody(String apiPath, String authHeader) throws Exception {
        URL url = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        int code = conn.getResponseCode();
        String body = new String(
                (code >= 400 ? conn.getErrorStream() : conn.getInputStream())
                        .readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return body;
    }

    // ========== 测试用例 ==========

    @Test
    @Order(1)
    @DisplayName("✅ 登录成功，应返回 JWT Token")
    void loginSuccess() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        String json = postJson("/api/auth/login", objectMapper.writeValueAsString(req));
        JsonNode root = objectMapper.readTree(json);

        assertEquals(200, root.get("code").asInt(), "登录应返回 code=200");
        assertNotNull(root.get("data"), "登录应包含 data");

        JsonNode data = root.get("data");
        jwtToken = data.get("token").asText();
        String username = data.get("username").asText();

        assertNotNull(jwtToken, "Token 不应为 null");
        assertFalse(jwtToken.isEmpty(), "Token 不应为空");
        assertEquals(TEST_USERNAME, username, "用户名应匹配");

        System.out.println("✅ 登录成功，Token: " + jwtToken);
    }

    @Test
    @Order(2)
    @DisplayName("❌ 错误密码登录应被拒绝")
    void loginWithWrongPassword() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrong_password");

        String json = postJson("/api/auth/login", objectMapper.writeValueAsString(req));
        JsonNode root = objectMapper.readTree(json);

        assertNotEquals(200, root.get("code").asInt(), "密码错误应返回非 200");
        System.out.println("✅ 错误密码被正确拒绝");
    }

    @Test
    @Order(3)
    @DisplayName("🔒 不带 Token 访问业务接口应返回 401")
    void accessWithoutToken() throws Exception {
        int status = getStatus("/api/test/protected", null);
        assertEquals(401, status, "未携带 Token 应返回 401");
        System.out.println("✅ 未携带 Token 返回 401");
    }

    @Test
    @Order(4)
    @DisplayName("🔒 带无效 Token 访问应返回 401")
    void accessWithInvalidToken() throws Exception {
        int status = getStatus("/api/test/protected", "Bearer invalid_token_here");
        assertEquals(401, status, "无效 Token 应返回 401");
        System.out.println("✅ 无效 Token 返回 401");
    }

    @Test
    @Order(5)
    @DisplayName("🔓 携带有效 Token 通过认证")
    void accessWithValidToken() throws Exception {
        if (jwtToken == null) {
            loginSuccess();
        }

        String json = getBody("/api/test/protected", "Bearer " + jwtToken);
        JsonNode root = objectMapper.readTree(json);

        assertEquals(200, root.get("code").asInt(), "应返回 code=200");
        assertEquals("认证通过", root.get("data").asText(), "应返回正确消息");
        System.out.println("✅ 有效 Token 通过认证，返回: " + json);
    }
}
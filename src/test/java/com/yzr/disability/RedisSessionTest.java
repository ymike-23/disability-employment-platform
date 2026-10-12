package com.yzr.disability;

import com.yzr.disability.config.JwtUtil;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.service.TokenService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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

/**
 * Redis + JWT 会话校验集成验证（仅验证认证基础设施，非业务模块）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestProtectedController.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisSessionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_USERNAME = "redis_test_admin";
    private static final String TEST_PASSWORD = "123456";
    private static final String KEY_PREFIX = "login:token:";

    private static String token;
    private static Long userId;

    @BeforeAll
    void setup() {
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
        if (userId != null) {
            stringRedisTemplate.delete(KEY_PREFIX + userId);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String postJson(String apiPath, String json, String authHeader) throws Exception {
        URL u = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        conn.setDoOutput(true);
        if (json != null) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        String body = new String((code >= 400 ? conn.getErrorStream() : conn.getInputStream())
                .readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return body;
    }

    private int getStatus(String apiPath, String authHeader) throws Exception {
        URL u = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private String loginAndGetToken() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        String body = postJson("/api/auth/login", objectMapper.writeValueAsString(req), null);
        JsonNode root = objectMapper.readTree(body);
        assertEquals(200, root.get("code").asInt(), "登录应成功");
        return root.get("data").get("token").asText();
    }

    @Test
    @Order(1)
    @DisplayName("✅ 登录成功，Redis 存在 login:token:{userId} 且 TTL>0")
    void loginAndRedisTokenExists() throws Exception {
        token = loginAndGetToken();
        assertNotNull(token);
        assertFalse(token.isEmpty());

        userId = jwtUtil.getUserIdFromToken(token);
        assertNotNull(userId, "Token 应包含 userId");

        String key = KEY_PREFIX + userId;
        String redisToken = stringRedisTemplate.opsForValue().get(key);
        assertNotNull(redisToken, "Redis 应存在 key: " + key);
        assertEquals(token, redisToken, "Redis 中 Token 应与登录返回一致");

        Long ttl = stringRedisTemplate.getExpire(key);
        assertTrue(ttl != null && ttl > 0, "TTL 应 > 0，实际: " + ttl);
        System.out.println("✅ 登录成功，Redis key=" + key + ", TTL=" + ttl + "s");
    }

    @Test
    @Order(2)
    @DisplayName("✅ 携带有效 Token（JWT+Redis 一致）可访问受保护接口")
    void accessWithValidToken() throws Exception {
        int status = getStatus("/api/test/protected", "Bearer " + token);
        assertEquals(200, status, "Token 有效应返回 200");
        System.out.println("✅ 携带有效 Token 访问受保护接口成功");
    }

    @Test
    @Order(3)
    @DisplayName("🔒 手动删除 Redis Token 后，原 JWT 请求必须 401")
    void deletedRedisTokenShouldBe401() throws Exception {
        stringRedisTemplate.delete(KEY_PREFIX + userId);
        assertNull(stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId), "Redis Token 应已删除");

        int status = getStatus("/api/test/protected", "Bearer " + token);
        assertEquals(401, status, "Redis Token 已删除，原 JWT 应返回 401");
        System.out.println("✅ Redis Token 删除后原 JWT 返回 401");
    }

    @Test
    @Order(4)
    @DisplayName("🔒 调用 logout 后，原 JWT 再次请求必须 401")
    void logoutShouldInvalidateToken() throws Exception {
        // 重新登录，重建 Redis 会话
        String newToken = loginAndGetToken();
        assertEquals(200, getStatus("/api/test/protected", "Bearer " + newToken), "重新登录后应可访问");

        // 退出登录
        String body = postJson("/api/auth/logout", null, "Bearer " + newToken);
        JsonNode root = objectMapper.readTree(body);
        assertEquals(200, root.get("code").asInt(), "logout 应成功");

        // Redis Token 应已删除
        assertNull(stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId), "logout 后 Redis Token 应已删除");

        // 原 Token 再次请求必须 401
        int status = getStatus("/api/test/protected", "Bearer " + newToken);
        assertEquals(401, status, "logout 后原 JWT 应返回 401");
        System.out.println("✅ logout 后原 JWT 返回 401，Redis Token 已清除");
    }
}
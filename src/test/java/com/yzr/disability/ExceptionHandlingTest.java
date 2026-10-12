package com.yzr.disability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局异常处理验证（仅验证基础设施，非业务模块）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ExceptionHandlingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 发送 JSON 请求，返回响应体 */
    private String request(String method, String apiPath, String body) throws Exception {
        URL u = URI.create(url(apiPath)).toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setDoOutput(true);
        if (body != null) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        String resp = new String((code >= 400 ? conn.getErrorStream() : conn.getInputStream())
                .readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return resp;
    }

    private int codeOf(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asInt();
    }

    @Test
    @DisplayName("✅ 无效 JSON 请求体 → 400（而非 500）")
    void invalidJsonShouldReturn400() throws Exception {
        String resp = request("POST", "/api/auth/login", "{invalid json");
        assertEquals(400, codeOf(resp), "无效 JSON 应返回 400，实际响应: " + resp);
        System.out.println("✅ 无效 JSON → 400: " + resp);
    }

    @Test
    @DisplayName("✅ 请求方法不支持 → 405")
    void methodNotSupportedShouldReturn405() throws Exception {
        // 登录接口仅支持 POST，用 GET 触发 405
        String resp = request("GET", "/api/auth/login", null);
        assertEquals(405, codeOf(resp), "错误请求方法应返回 405，实际响应: " + resp);
        System.out.println("✅ 方法不支持 → 405: " + resp);
    }

    @Test
    @DisplayName("✅ 资源不存在 → 404（而非 500）")
    void resourceNotFoundShouldReturn404() throws Exception {
        String resp = request("GET", "/swagger-ui/definitely-not-exist.html", null);
        assertEquals(404, codeOf(resp), "不存在资源应返回 404，实际响应: " + resp);
        System.out.println("✅ 资源不存在 → 404: " + resp);
    }

    @Test
    @DisplayName("✅ 业务异常保持原有处理（用户名不存在 → 500 且返回业务提示）")
    void businessExceptionKeepsOriginalBehaviour() throws Exception {
        String resp = request("POST", "/api/auth/login",
                "{\"username\":\"no_such_user_xyz\",\"password\":\"whatever\"}");
        JsonNode root = objectMapper.readTree(resp);
        assertEquals(500, root.get("code").asInt(), "业务异常应保持 code=500");
        assertEquals("用户名或密码错误", root.get("message").asText(), "应返回业务提示而非通用错误");
        System.out.println("✅ 业务异常 → 500 且保留业务提示: " + resp);
    }

    @Test
    @DisplayName("✅ 未知异常不暴露堆栈/敏感信息")
    void unknownExceptionShouldNotExposeDetails() throws Exception {
        String resp = request("POST", "/api/auth/login", "{invalid json");
        assertFalse(resp.contains("Exception"), "响应不应包含异常类名");
        assertFalse(resp.contains("stackTrace"), "响应不应包含堆栈信息");
        assertFalse(resp.contains("SQL"), "响应不应包含 SQL 信息");
        JsonNode root = objectMapper.readTree(resp);
        assertNotNull(root.get("message").asText(), "应包含 message 字段");
        System.out.println("✅ 异常响应未暴露敏感信息");
    }
}
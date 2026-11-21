package com.yzr.disability;

import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.config.JwtUtil;
import com.yzr.disability.entity.dto.BusinessUserQueryDTO;
import com.yzr.disability.entity.dto.UpdateBusinessUserDTO;
import com.yzr.disability.service.TokenService;
import com.yzr.disability.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** HTTP、网格归属和事务验收；仅使用H2与内存会话。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_crud_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AdminUserCrudTest {
    private static final String API = "/api/admin/users";
    private static final String PWD = "Crud@123456";
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwt;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserService users;
    @MockitoBean private TokenService sessions;
    private final Map<Long, String> tokens = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String prefix;
    private long admin;
    private long business;
    private long ownGrid;
    private long freeGrid;
    private String adminToken;
    private String businessToken;

    @BeforeEach
    void setup() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:admin_crud_test"));
        }
        when(sessions.getToken(anyLong())).thenAnswer(call -> tokens.get(call.getArgument(0)));
        doAnswer(call -> { tokens.put(call.getArgument(0), call.getArgument(1)); return null; }).when(sessions).saveToken(anyLong(), anyString());
        doAnswer(call -> { tokens.remove(call.getArgument(0)); return null; }).when(sessions).deleteToken(anyLong());
        prefix = "crud" + Long.toString(System.nanoTime(), 36);
        admin = user("admin", "ADMIN");
        business = user("biz", "BUSINESS");
        ownGrid = grid(business);
        freeGrid = grid(null);
        adminToken = token(admin);
        businessToken = token(business);
    }

    @Test
    void pageFiltersOnlyBusinessAndNeverLeaksPassword() throws Exception {
        JsonNode page = request("GET", API + "/page?username=" + name(business) + "&size=1", null, adminToken);
        assertEquals(200, code(page));
        assertEquals(1, page.at("/data/total").asInt());
        JsonNode record = page.at("/data/records/0");
        assertEquals(business, record.get("id").asLong());
        assertEquals(ownGrid, record.get("gridId").asLong());
        assertEquals(1, record.get("grids").size());
        assertFalse(record.has("password"));
        assertEquals(0, request("GET", API + "/page?username=" + name(admin), null, adminToken).at("/data/total").asInt());
        for (String query : List.of("current=0", "size=101", "status=2")) assertEquals(400, code(request("GET", API + "/page?" + query, null, adminToken)));
    }

    @Test
    void createAndReadRealAccountAndGrid() throws Exception {
        String username = prefix + "new";
        JsonNode result = request("POST", "/api/admin/addusers", Map.of("username", username, "password", PWD, "gridId", freeGrid), adminToken);
        assertEquals(200, code(result));
        long id = result.at("/data/id").asLong();
        JsonNode detail = request("GET", API + "/" + id, null, adminToken).get("data");
        assertEquals(username, detail.get("username").asText());
        assertEquals(freeGrid, detail.get("grids").get(0).get("id").asLong());
        assertFalse(detail.has("password"));
        assertTrue(encoder.matches(PWD, jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?", String.class, id)));
    }

    @Test
    void editRenameDisableAndReenablePreservesPasswordAndGrid() throws Exception {
        String hash = jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?", String.class, business);
        String renamed = prefix + "renamed";
        assertEquals(200, code(request("PUT", API + "/" + business, edit(renamed, 0, ownGrid), adminToken)));
        assertEquals(business, worker(ownGrid));
        assertEquals(renamed, jdbc.queryForObject("SELECT grid_worker_name FROM sys_grid WHERE id=?", String.class, ownGrid));
        assertEquals(hash, jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?", String.class, business));
        assertEquals(401, code(request("GET", "/api/dict/list?dictType=gender", null, businessToken)));
        JsonNode disabledLogin = request("POST", "/api/auth/login", Map.of("username", renamed, "password", PWD), null);
        // 登录接口沿用现有异常码；这里验证停用确实拒绝登录及其明确提示。
        assertNotEquals(200, code(disabledLogin));
        assertTrue(disabledLogin.get("message").asText().contains("账户已被禁用"));
        assertEquals(200, code(request("PUT", API + "/" + business, edit(renamed, 1, ownGrid), adminToken)));
        assertEquals(200, code(request("POST", "/api/auth/login", Map.of("username", renamed, "password", PWD), null)));
    }

    @Test
    void reassignChangesScopeWithoutMovingPersonRecords() throws Exception {
        long oldPerson = person(ownGrid);
        long newPerson = person(freeGrid);
        assertEquals(200, code(request("GET", "/api/disabled-person/" + oldPerson, null, businessToken)));
        assertEquals(403, code(request("GET", "/api/disabled-person/" + newPerson, null, businessToken)));
        assertEquals(200, code(request("PUT", API + "/" + business, edit(name(business), 1, freeGrid), adminToken)));
        assertNull(worker(ownGrid));
        assertEquals(business, worker(freeGrid));
        assertEquals(ownGrid, jdbc.queryForObject("SELECT grid_id FROM disabled_person WHERE id=?", Long.class, oldPerson));
        assertEquals(401, code(request("GET", "/api/disabled-person/" + oldPerson, null, businessToken)));
        String refreshed = login(name(business));
        assertEquals(200, code(request("GET", "/api/disabled-person/" + newPerson, null, refreshed)));
        assertEquals(403, code(request("GET", "/api/disabled-person/" + oldPerson, null, refreshed)));
        assertTrue(containsGrid(request("GET", "/api/admin/grids", null, adminToken), ownGrid));
        assertFalse(containsGrid(request("GET", "/api/admin/grids", null, adminToken), freeGrid));
    }

    @Test
    void occupiedDisabledMissingAndInvalidInputsNeverOverwriteAssignments() throws Exception {
        long other = user("other", "BUSINESS");
        long occupied = grid(other);
        assertEquals(409, code(request("PUT", API + "/" + business, edit(prefix + "wrong", 0, occupied), adminToken)));
        assertEquals(business, worker(ownGrid));
        assertEquals(other, worker(occupied));
        assertEquals(400, code(request("PUT", API + "/" + business, edit(name(business), 2, ownGrid), adminToken)));
        assertEquals(400, code(request("PUT", API + "/" + business, edit(" ", 1, ownGrid), adminToken)));
        assertEquals(400, code(request("PUT", API + "/" + business, edit(name(business), 1, Long.MAX_VALUE), adminToken)));
        jdbc.update("UPDATE sys_grid SET status=0 WHERE id=?", freeGrid);
        assertEquals(400, code(request("PUT", API + "/" + business, edit(name(business), 1, freeGrid), adminToken)));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM sys_user WHERE id=?", Integer.class, business));
        assertEquals(businessToken, tokens.get(business));
    }

    @Test
    void duplicateUsernameRollsBackUserAndGridChanges() throws Exception {
        String previousName = name(business);
        assertEquals(409, code(request("PUT", API + "/" + business, edit(name(admin), 0, freeGrid), adminToken)));
        assertEquals(previousName, name(business));
        assertEquals(business, worker(ownGrid));
        assertNull(worker(freeGrid));
        assertEquals(businessToken, tokens.get(business));
    }

    @Test
    void logicalDeleteReleasesGridAndPreservesPersonAndRoleHistory() throws Exception {
        long person = person(ownGrid);
        assertEquals(200, code(request("DELETE", API + "/" + business, null, adminToken)));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM sys_user WHERE id=?", Integer.class, business));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM sys_user WHERE id=?", Integer.class, business));
        assertNull(worker(ownGrid));
        assertEquals(ownGrid, jdbc.queryForObject("SELECT grid_id FROM disabled_person WHERE id=?", Long.class, person));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sys_user_role WHERE user_id=?", Integer.class, business));
        assertFalse(tokens.containsKey(business));
        assertEquals(404, code(request("GET", API + "/" + business, null, adminToken)));
        assertEquals(401, code(request("GET", "/api/disabled-person/" + person, null, businessToken)));
        assertTrue(containsGrid(request("GET", "/api/admin/grids", null, adminToken), ownGrid));
    }

    @Test
    void adminOtherRolesAndMixedAdminRoleAreProtected() throws Exception {
        long worker = user("worker", "GRID_WORKER");
        long dual = user("dual", "BUSINESS");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='ADMIN' AND deleted=0", dual);
        for (long protectedId : List.of(admin, worker, dual)) {
            assertEquals(404, code(request("GET", API + "/" + protectedId, null, adminToken)));
            assertEquals(404, code(request("PUT", API + "/" + protectedId, edit(prefix + "forbidden", 0, freeGrid), adminToken)));
            assertEquals(404, code(request("DELETE", API + "/" + protectedId, null, adminToken)));
        }
        assertEquals(0, request("GET", API + "/page?username=" + name(dual), null, adminToken).at("/data/total").asInt());
    }

    @Test
    void businessAndAnonymousCannotUseAdminCrud() throws Exception {
        for (String auth : List.of(businessToken, "")) {
            int expected = auth.isEmpty() ? 401 : 403;
            assertEquals(expected, code(request("GET", API + "/page", null, auth)));
            assertEquals(expected, code(request("GET", API + "/" + business, null, auth)));
            assertEquals(expected, code(request("PUT", API + "/" + business, edit(name(business), 0, freeGrid), auth)));
            assertEquals(expected, code(request("DELETE", API + "/" + business, null, auth)));
        }
        var previous = SecurityContextHolder.getContext();
        try {
            var principal = new LoginUser(business, name(business), "", true, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS")));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            assertEquals(403, assertThrows(BusinessException.class, () -> users.pageBusinessUsers(new BusinessUserQueryDTO())).getCode());
            assertEquals(403, assertThrows(BusinessException.class, () -> users.getBusinessUser(business)).getCode());
            assertEquals(403, assertThrows(BusinessException.class, () -> users.updateBusinessUser(business, new UpdateBusinessUserDTO())).getCode());
            assertEquals(403, assertThrows(BusinessException.class, () -> users.deleteBusinessUser(business)).getCode());
        } finally { SecurityContextHolder.setContext(previous); }
        assertEquals(business, worker(ownGrid));
    }

    @Test
    void multipleLegacyGridsAreVisibleAndCannotBeSilentlyReplaced() throws Exception {
        long second = grid(business);
        JsonNode detail = request("GET", API + "/" + business, null, adminToken).get("data");
        assertEquals(2, detail.get("grids").size());
        assertTrue(detail.get("gridId").isNull());
        assertEquals(409, code(request("PUT", API + "/" + business, edit(name(business), 1, freeGrid), adminToken)));
        assertEquals(business, worker(ownGrid));
        assertEquals(business, worker(second));
    }

    @Test
    void sessionFailureRollsBackEntireUpdateAndDelete() throws Exception {
        String previousName = name(business);
        doThrow(new IllegalStateException("isolated session failure")).when(sessions).deleteToken(business);
        assertEquals(500, code(request("PUT", API + "/" + business, edit(prefix + "rollback", 0, freeGrid), adminToken)));
        assertEquals(previousName, name(business));
        assertEquals(business, worker(ownGrid));
        assertNull(worker(freeGrid));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM sys_user WHERE id=?", Integer.class, business));
        assertEquals(500, code(request("DELETE", API + "/" + business, null, adminToken)));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM sys_user WHERE id=?", Integer.class, business));
        assertEquals(business, worker(ownGrid));
    }

    @Test
    void concurrentReassignmentHasOneWinnerAndKeepsLosersOriginalGrid() throws Exception {
        long second = user("second", "BUSINESS");
        long secondGrid = grid(second);
        String firstName = name(business);
        String secondName = name(second);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); return request("PUT", API + "/" + business, edit(firstName, 1, freeGrid), adminToken); });
            var next = executor.submit(() -> { gate.await(); return request("PUT", API + "/" + second, edit(secondName, 1, freeGrid), adminToken); });
            gate.countDown();
            int firstCode = code(first.get(20, TimeUnit.SECONDS));
            int secondCode = code(next.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(200,409), List.of(firstCode, secondCode).stream().sorted().toList());
            long winner = firstCode == 200 ? business : second;
            assertEquals(winner, worker(freeGrid));
            assertEquals(firstCode == 200 ? null : business, worker(ownGrid));
            assertEquals(secondCode == 200 ? null : second, worker(secondGrid));
        }
    }

    @Test
    void tokenIdentityCannotFollowAReusedUsername() throws Exception {
        String mismatched = jwt.generateToken(business, name(admin));
        tokens.put(business, mismatched);
        assertEquals(401, code(request("GET", API + "/page", null, mismatched)));
    }

    private Map<String, Object> edit(String username, int status, long grid) { return Map.of("username", username, "status", status, "gridId", grid); }
    private String name(long id) { return jdbc.queryForObject("SELECT username FROM sys_user WHERE id=?", String.class, id); }
    private Long worker(long grid) { return jdbc.queryForObject("SELECT grid_worker_id FROM sys_grid WHERE id=?", Long.class, grid); }
    private long user(String suffix, String role) {
        String username = prefix + suffix;
        jdbc.update("INSERT INTO sys_user(username,password,status,deleted) VALUES(?,?,1,0)", username, encoder.encode(PWD));
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=? AND deleted=0", id, role);
        return id;
    }
    private String token(long id) { String value = jwt.generateToken(id, name(id)); tokens.put(id, value); return value; }
    private long grid(Long worker) {
        String code = "crud" + Long.toString(System.nanoTime(), 36);
        jdbc.update("INSERT INTO sys_grid(grid_name,grid_code,grid_worker_id,status,deleted) VALUES(?,?,?,1,0)", code, code, worker);
        return jdbc.queryForObject("SELECT id FROM sys_grid WHERE grid_code=?", Long.class, code);
    }
    private long person(long grid) {
        String identity = Long.toString(System.nanoTime());
        jdbc.update("INSERT INTO disabled_person(name,id_card,grid_id,status,deleted) VALUES(?,?,?,1,0)", prefix, identity, grid);
        return jdbc.queryForObject("SELECT id FROM disabled_person WHERE id_card=?", Long.class, identity);
    }
    private String login(String username) throws Exception {
        JsonNode response = request("POST", "/api/auth/login", Map.of("username", username, "password", PWD), null);
        assertEquals(200, code(response));
        return response.at("/data/token").asText();
    }
    private boolean containsGrid(JsonNode response, long id) { for (JsonNode grid : response.get("data")) if (grid.get("id").asLong() == id) return true; return false; }
    private int code(JsonNode response) { return response.get("code").asInt(); }
    private JsonNode request(String method, String url, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + url)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body());
    }
}

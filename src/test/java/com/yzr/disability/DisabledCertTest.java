package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.DisabledCertPO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.mapper.DisabledCertMapper;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.service.DisabledCertService;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 独立证件模块集成测试，数据仅写入 H2 测试库。 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisabledCertTest {

    private static final String API = "/api/disabled-cert";
    private static final String PWD = "Test@123456";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DisabledCertMapper disabledCertMapper;
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
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private Long personAId;
    private Long personBId;
    private Long currentAId;
    private Long historyAId;
    private Long currentBId;
    private LocalDate today;

    @BeforeAll
    void setupUsers() throws Exception {
        createUser("dc_test_admin", "ADMIN");
        Long businessAId = createUser("dc_test_business_a", "BUSINESS");
        Long businessBId = createUser("dc_test_business_b", "BUSINESS");
        gridAId = createGrid("证件测试网格A", businessAId);
        gridBId = createGrid("证件测试网格B", businessBId);
        adminToken = login("dc_test_admin");
        businessToken = login("dc_test_business_a");
    }

    @BeforeEach
    void setupCertificates() {
        // 每个用例使用独立人员及证件，不依赖测试执行顺序。
        today = LocalDate.now();
        personAId = createPerson("证件测试人员A", gridAId);
        personBId = createPerson("证件测试人员B", gridBId);
        currentAId = seedCert(personAId, "CURRENT-A-" + personAId, 1);
        historyAId = seedCert(personAId, "HISTORY-A-" + personAId, 0);
        currentBId = seedCert(personBId, "CURRENT-B-" + personBId, 1);
    }

    @AfterAll
    void cleanupTokens() {
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("新增证件，数据独立存入 disabled_cert")
    void createCertificate() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("certNo", "NEW-" + personAId);
        body.put("certType", "残疾人证");
        body.put("issueDate", today.minusDays(30).toString());
        body.put("expireDate", today.plusDays(300).toString());
        body.put("issueOrg", "呼和浩特市残疾人联合会");
        body.put("disabilityType", "肢体");
        body.put("disabilityLevel", "二级");
        body.put("certStatus", DisabledCertService.CERT_STATUS_NORMAL);
        body.put("isCurrent", 0);
        body.put("remark", "证件录入测试");

        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        DisabledCertPO po = disabledCertMapper.selectById(response.get("data").asLong());
        assertNotNull(po);
        assertEquals(personAId, po.getPersonId());
        assertEquals("NEW-" + personAId, po.getCertNo());
        assertEquals("残疾人证", po.getCertType());
        assertEquals(today.minusDays(30), po.getIssueDate());
        assertEquals(today.plusDays(300), po.getExpireDate());
        assertEquals("呼和浩特市残疾人联合会", po.getIssueOrg());
        assertEquals("肢体", po.getDisabilityType());
        assertEquals("二级", po.getDisabilityLevel());
        assertEquals(DisabledCertService.CERT_STATUS_NORMAL, po.getCertStatus());
        assertEquals(0, po.getIsCurrent());
        assertEquals(0, po.getDeleted());
        assertEquals("证件录入测试", po.getRemark());
        assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
    }

    @Test
    @DisplayName("查询当前证件仅返回 is_current=1 的记录")
    void queryCurrentCertificate() throws Exception {
        JsonNode data = currentData(personAId, businessToken);
        assertEquals(currentAId.longValue(), data.get("id").asLong());
        assertEquals(personAId.longValue(), data.get("personId").asLong());
        assertEquals("CURRENT-A-" + personAId, data.get("certNo").asText());
        assertEquals(today.minusDays(100).toString(), data.get("issueDate").asText());
        assertEquals(today.plusDays(100).toString(), data.get("expireDate").asText());
        assertEquals(1, data.get("isCurrent").asInt());
        assertFalse(data.has("deleted"));
    }

    @Test
    @DisplayName("证件列表按 personId 查询，排除已逻辑删除记录")
    void listCertificatesByPerson() throws Exception {
        Long deletedId = seedCert(personAId, "DELETED-" + personAId, 0);
        disabledCertMapper.deleteById(deletedId);

        List<Long> ids = listIds(personAId, businessToken);
        assertEquals(List.of(currentAId, historyAId), ids);
        assertFalse(ids.contains(deletedId));
        assertFalse(ids.contains(currentBId));
    }

    @Test
    @DisplayName("无证件人员的当前证件为 null，证件列表为空")
    void personWithoutCertificate() throws Exception {
        Long personId = createPerson("暂无证件人员", gridAId);
        assertTrue(currentData(personId, businessToken).isNull());
        assertTrue(listIds(personId, businessToken).isEmpty());
    }

    @Test
    @DisplayName("新增当前证件时将旧当前证件改为历史，其他人员不受影响")
    void createSwitchesCurrentCertificate() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("certNo", "REPLACEMENT-" + personAId);
        // 不传 isCurrent，验证新增默认当前证件。
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        Long newId = response.get("data").asLong();

        assertEquals(0, disabledCertMapper.selectById(currentAId).getIsCurrent());
        assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
        assertEquals(1, disabledCertMapper.selectById(newId).getIsCurrent());
        assertEquals(1, disabledCertMapper.selectById(currentBId).getIsCurrent());
        assertEquals(1L, currentCount(personAId));
        assertEquals(newId.longValue(), currentData(personAId, businessToken).get("id").asLong());
    }

    @Test
    @DisplayName("修改证件信息，未传字段及当前标识保持不变")
    void updateCertificate() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("certNo", "UPDATED-" + personAId);
        body.put("issueOrg", "新城区残联");
        body.put("expireDate", today.plusDays(200).toString());
        body.put("certStatus", DisabledCertService.CERT_STATUS_RENEWING);
        assertSuccess(request("PUT", API + "/" + currentAId, body, businessToken));

        DisabledCertPO po = disabledCertMapper.selectById(currentAId);
        assertEquals("UPDATED-" + personAId, po.getCertNo());
        assertEquals("新城区残联", po.getIssueOrg());
        assertEquals(today.plusDays(200), po.getExpireDate());
        assertEquals(today.minusDays(100), po.getIssueDate());
        assertEquals(DisabledCertService.CERT_STATUS_RENEWING, po.getCertStatus());
        assertEquals(personAId, po.getPersonId());
        assertEquals(1, po.getIsCurrent());
    }

    @Test
    @DisplayName("修改历史证件为当前证件时取消旧当前标识")
    void updateSwitchesCurrentCertificate() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("isCurrent", 1);
        assertSuccess(request("PUT", API + "/" + historyAId, body, businessToken));

        assertEquals(0, disabledCertMapper.selectById(currentAId).getIsCurrent());
        assertEquals(1, disabledCertMapper.selectById(historyAId).getIsCurrent());
        assertEquals(1, disabledCertMapper.selectById(currentBId).getIsCurrent());
        assertEquals(1L, currentCount(personAId));
        assertEquals(historyAId.longValue(), currentData(personAId, businessToken).get("id").asLong());
    }

    @Test
    @DisplayName("删除证件为逻辑删除，不自动启用历史证件")
    void deleteCertificateLogically() throws Exception {
        assertSuccess(request("DELETE", API + "/" + currentAId, null, businessToken));

        assertNull(disabledCertMapper.selectById(currentAId), "普通查询应排除已删除证件");
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT deleted FROM disabled_cert WHERE id = ?", Integer.class, currentAId),
                "数据库仍保留记录，deleted 应为 1");
        assertEquals(List.of(historyAId), listIds(personAId, businessToken));
        assertTrue(currentData(personAId, businessToken).isNull());
        assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
        assertNotNull(disabledPersonMapper.selectById(personAId), "删除证件不删除人员档案");
    }

    @Test
    @DisplayName("BUSINESS 无法查询、新增、修改或删除他网格人员的证件")
    void businessCannotOperateOtherGrid() throws Exception {
        assertEquals(403, request("GET", API + "/list?personId=" + personBId, null, businessToken)
                .get("code").asInt());
        assertEquals(403, request("GET", API + "/current?personId=" + personBId, null, businessToken)
                .get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(personBId), businessToken).get("code").asInt());

        // 伪造入参中的 personId 也不能绕过数据库中证件的真实归属。
        Map<String, Object> forgedBody = saveBody(personAId);
        forgedBody.put("issueOrg", "越权修改");
        assertEquals(403, request("PUT", API + "/" + currentBId, forgedBody, businessToken)
                .get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + currentBId, null, businessToken)
                .get("code").asInt());
        DisabledCertPO po = disabledCertMapper.selectById(currentBId);
        assertNotNull(po);
        assertEquals(personBId, po.getPersonId());
        assertEquals("测试发证机关", po.getIssueOrg());
        assertEquals(1L, currentCount(personBId));
    }

    @Test
    @DisplayName("ADMIN 可跨网格维护证件")
    void adminCanOperateOtherGrid() throws Exception {
        assertEquals(currentBId.longValue(), currentData(personBId, adminToken).get("id").asLong());
        Map<String, Object> body = saveBody(personBId);
        body.put("issueOrg", "管理员修改机关");
        assertSuccess(request("PUT", API + "/" + currentBId, body, adminToken));
        assertEquals("管理员修改机关", disabledCertMapper.selectById(currentBId).getIssueOrg());
    }

    @Test
    @DisplayName("修改证件不允许更换所属人员")
    void cannotChangeCertificateOwner() throws Exception {
        JsonNode response = request("PUT", API + "/" + currentAId, saveBody(personBId), adminToken);
        assertEquals(400, response.get("code").asInt());
        assertEquals(personAId, disabledCertMapper.selectById(currentAId).getPersonId());
    }

    @Test
    @DisplayName("部分更新日期也要检查已保存的发证日期")
    void invalidPartialDateUpdateRejected() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("expireDate", today.minusDays(101).toString());
        JsonNode response = request("PUT", API + "/" + currentAId, body, businessToken);
        assertEquals(400, response.get("code").asInt());
        assertEquals(today.plusDays(100), disabledCertMapper.selectById(currentAId).getExpireDate());
        assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
    }

    private Long createUser(String username, String roleCode) {
        LambdaQueryWrapper<SysRolePO> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(SysRolePO::getRoleCode, roleCode);
        SysRolePO role = sysRoleMapper.selectOne(roleQuery);
        assertNotNull(role, "测试角色应由 schema-test.sql 预置");
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

    private Long createGrid(String name, Long workerId) {
        SysGridPO grid = new SysGridPO();
        grid.setGridName(name);
        grid.setGridWorkerId(workerId);
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

    private Long seedCert(Long personId, String certNo, int isCurrent) {
        DisabledCertPO cert = new DisabledCertPO();
        cert.setPersonId(personId);
        cert.setCertNo(certNo);
        cert.setIssueDate(today.minusDays(100));
        cert.setExpireDate(today.plusDays(100));
        cert.setIssueOrg("测试发证机关");
        cert.setCertStatus(DisabledCertService.CERT_STATUS_NORMAL);
        cert.setIsCurrent(isCurrent);
        cert.setDeleted(0);
        disabledCertMapper.insert(cert);
        return cert.getId();
    }

    private long currentCount(Long personId) {
        LambdaQueryWrapper<DisabledCertPO> query = new LambdaQueryWrapper<>();
        query.eq(DisabledCertPO::getPersonId, personId).eq(DisabledCertPO::getIsCurrent, 1);
        return disabledCertMapper.selectCount(query);
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

    private JsonNode currentData(Long personId, String token) throws Exception {
        JsonNode response = request("GET", API + "/current?personId=" + personId, null, token);
        assertSuccess(response);
        return response.get("data");
    }

    private List<Long> listIds(Long personId, String token) throws Exception {
        JsonNode response = request("GET", API + "/list?personId=" + personId, null, token);
        assertSuccess(response);
        List<Long> ids = new ArrayList<>();
        for (JsonNode cert : response.get("data")) {
            ids.add(cert.get("id").asLong());
        }
        return ids;
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

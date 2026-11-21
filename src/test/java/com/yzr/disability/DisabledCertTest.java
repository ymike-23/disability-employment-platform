package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
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
import com.yzr.disability.service.DictService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    @Autowired
    private DisabledCertService disabledCertService;
    @Autowired
    private DictService dictService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long adminUserId;
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private String workerToken;
    private String noGridToken;
    private Long personAId;
    private Long personBId;
    private Long currentAId;
    private Long historyAId;
    private Long currentBId;
    private LocalDate today;

    @BeforeAll
    void setupUsers() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:"));
        }
        adminUserId = createUser("dc_test_admin", "ADMIN");
        Long businessAId = createUser("dc_test_business_a", "BUSINESS");
        Long businessBId = createUser("dc_test_business_b", "BUSINESS");
        createUser("dc_test_worker", "GRID_WORKER");
        createUser("dc_test_no_grid", "BUSINESS");
        gridAId = createGrid("证件测试网格A", businessAId);
        gridBId = createGrid("证件测试网格B", businessBId);
        adminToken = login("dc_test_admin");
        businessToken = login("dc_test_business_a");
        workerToken = login("dc_test_worker");
        noGridToken = login("dc_test_no_grid");
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
        body.put("certType", "1");
        body.put("issueDate", today.minusDays(30).toString());
        body.put("expireDate", today.plusDays(300).toString());
        body.put("issueOrg", "呼和浩特市残疾人联合会");
        body.put("disabilityType", "1");
        body.put("disabilityLevel", "2");
        body.put("certStatus", DisabledCertService.CERT_STATUS_NORMAL);
        body.put("isCurrent", 0);
        body.put("remark", "证件录入测试");

        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        DisabledCertPO po = disabledCertMapper.selectById(response.get("data").asLong());
        assertNotNull(po);
        assertEquals(personAId, po.getPersonId());
        assertEquals("NEW-" + personAId, po.getCertNo());
        assertEquals("1", po.getCertType());
        assertEquals(today.minusDays(30), po.getIssueDate());
        assertEquals(today.plusDays(300), po.getExpireDate());
        assertEquals("呼和浩特市残疾人联合会", po.getIssueOrg());
        assertEquals("1", po.getDisabilityType());
        assertEquals("2", po.getDisabilityLevel());
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
        assertEquals("1", disabledCertMapper.selectById(newId).getCertStatus());
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

    @Test
    @DisplayName("字典接口返回独立value/label结构，仅ADMIN和BUSINESS可以查询")
    void dictionaryHttpContractAndRoleAccess() throws Exception {
        Map<String, List<String>> expected = Map.of(
                "cert_status", List.of("0", "1", "2", "3", "4"),
                "cert_type", List.of("1", "2", "3"),
                "disability_type", List.of("1", "2", "3", "4", "5", "6", "7"),
                "disability_level", List.of("1", "2", "3", "4"));
        for (var dictionary : expected.entrySet()) {
            JsonNode response = request("GET", "/api/dict/list?dictType=" + dictionary.getKey(), null, businessToken);
            assertSuccess(response);
            List<String> actual = new ArrayList<>();
            for (JsonNode option : response.get("data")) {
                assertEquals(2, option.size());
                assertTrue(option.get("value").isString());
                assertFalse(option.get("label").asText().isBlank());
                actual.add(option.get("value").asText());
                assertFalse(option.get("label").asText().contains("挂失"));
            }
            assertEquals(dictionary.getValue(), actual);
        }
        assertSuccess(request("GET", "/api/dict/list?dictType=cert_status", null, adminToken));
        assertSuccess(request("GET", "/api/dict/list?dictType=cert_status", null, noGridToken));
        assertCode(403, request("GET", "/api/dict/list?dictType=cert_status", null, workerToken));
        assertCode(401, request("GET", "/api/dict/list?dictType=cert_status", null, null));
        assertCode(400, request("GET", "/api/dict/list", null, businessToken));
        for (String invalid : List.of("", " ", "x".repeat(51))) {
            assertCode(400, request("GET", "/api/dict/list?dictType=" + enc(invalid), null, businessToken));
        }
        JsonNode absent = request("GET", "/api/dict/list?dictType=dc_nonexistent_dict", null, businessToken);
        assertSuccess(absent);
        assertEquals(0, absent.get("data").size());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_GRID_WORKER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("worker", null, authorities));
        try {
            assertEquals(403, assertThrows(BusinessException.class, () -> dictService.list("cert_status")).getCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("字典只返回启用条目，并按sort_order及ID稳定排序")
    void dictionaryExcludesDisabledValuesAndSorts() throws Exception {
        String type = "dc_test_order";
        try {
            jdbcTemplate.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,sort_order,status) VALUES(?,?,?,20,1)", type, "b", "后项");
            jdbcTemplate.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,sort_order,status) VALUES(?,?,?,5,1)", type, "a", "前项");
            jdbcTemplate.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,sort_order,status) VALUES(?,?,?,5,1)", type, "c", "同序后项");
            jdbcTemplate.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,sort_order,status) VALUES(?,?,?,-1,0)", type, "disabled", "禁用项");
            JsonNode response = request("GET", "/api/dict/list?dictType=" + type, null, businessToken);
            assertSuccess(response);
            List<String> values = new ArrayList<>();
            response.get("data").forEach(option -> values.add(option.get("value").asText()));
            assertEquals(List.of("a", "c", "b"), values);
        } finally {
            jdbcTemplate.update("DELETE FROM sys_dict WHERE dict_type=?", type);
        }
    }

    @Test
    @DisplayName("旧中文证件仅作为历史数据读取与筛选，修改其他字段不覆写旧值")
    void legacyChineseCertificateValuesRemainReadableAndUnchanged() throws Exception {
        jdbcTemplate.update("UPDATE disabled_cert SET cert_status=?,cert_type=?,disability_type=?,disability_level=? WHERE id=?",
                "正常", "残疾人证", "肢体", "二级", currentAId);
        JsonNode before = currentData(personAId, businessToken);
        assertEquals("正常", before.get("certStatus").asText());
        assertEquals("残疾人证", before.get("certType").asText());
        JsonNode filtered = request("GET", API + "/list?personId=" + personAId + "&certStatus=" + enc("正常"), null, businessToken);
        assertSuccess(filtered);
        assertEquals(1, filtered.get("data").size());
        assertEquals(currentAId.longValue(), filtered.get("data").get(0).get("id").asLong());
        Map<String, Object> body = saveBody(personAId);
        body.put("issueOrg", "仅修改发证机关");
        body.put("certStatus", null);
        body.put("certType", null);
        body.put("disabilityType", null);
        body.put("disabilityLevel", null);
        assertSuccess(request("PUT", API + "/" + currentAId, body, businessToken));
        DisabledCertPO after = disabledCertMapper.selectById(currentAId);
        assertEquals("仅修改发证机关", after.getIssueOrg());
        assertEquals("正常", after.getCertStatus());
        assertEquals("残疾人证", after.getCertType());
        assertEquals("肢体", after.getDisabilityType());
        assertEquals("二级", after.getDisabilityLevel());
    }

    @Test
    @DisplayName("证件新增明确提交的状态只保存启用dict_value，默认正常为1")
    void certificateStatusStoresDictionaryValues() throws Exception {
        for (String value : List.of("0", "1", "2", "3", "4")) {
            Map<String, Object> body = saveBody(personAId);
            body.put("certStatus", value);
            body.put("isCurrent", 0);
            JsonNode response = request("POST", API, body, businessToken);
            assertSuccess(response);
            assertEquals(value, disabledCertMapper.selectById(response.get("data").asLong()).getCertStatus());
        }
        Map<String, Object> body = saveBody(personAId);
        body.put("certStatus", null);
        body.put("isCurrent", 0);
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        assertEquals("1", disabledCertMapper.selectById(response.get("data").asLong()).getCertStatus());
    }

    @Test
    @DisplayName("写入枚举拒绝中文名称、未知值、空串及带空格的非精确字典值")
    void invalidDictionaryValuesAreRejectedBeforeCurrentChanges() throws Exception {
        Map<String, String> labels = Map.of("certType", "第二代残疾人证", "disabilityType", "肢体残疾",
                "disabilityLevel", "二级", "certStatus", "正常");
        for (var field : labels.entrySet()) {
            for (String invalid : List.of(field.getValue(), "999", "", "1 ")) {
                Map<String, Object> body = saveBody(personAId);
                body.put(field.getKey(), invalid);
                body.put("isCurrent", 1);
                assertCode(400, request("POST", API, body, businessToken));
                assertCode(400, request("PUT", API + "/" + historyAId, body, businessToken));
            }
        }
        Map<String, Object> lost = saveBody(personAId);
        lost.put("certStatus", "挂失");
        assertCode(400, request("POST", API, lost, businessToken));
        assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
        assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
        assertEquals(1, currentCount(personAId));
    }

    @Test
    @DisplayName("四类证件字典中的停用值均不可新增或显式修改")
    void disabledDictionaryValuesAreRejected() throws Exception {
        Map<String, String> types = Map.of("certType", "cert_type", "disabilityType", "disability_type",
                "disabilityLevel", "disability_level", "certStatus", "cert_status");
        String disabledValue = "dc-disabled";
        try {
            for (var field : types.entrySet()) {
                jdbcTemplate.update("INSERT INTO sys_dict(dict_type,dict_value,dict_label,status) VALUES(?,?,?,0)",
                        field.getValue(), disabledValue, "停用测试值");
                Map<String, Object> body = saveBody(personAId);
                body.put(field.getKey(), disabledValue);
                assertCode(400, request("POST", API, body, businessToken));
                assertCode(400, request("PUT", API + "/" + currentAId, body, businessToken));
            }
        } finally {
            for (String type : types.values()) {
                jdbcTemplate.update("DELETE FROM sys_dict WHERE dict_type=? AND dict_value=?", type, disabledValue);
            }
        }
        assertEquals(1, currentCount(personAId));
    }

    @Test
    @DisplayName("新增默认正常状态也必须存在启用字典项，失败不切换当前证件")
    void disabledDefaultStatusPreventsCreation() throws Exception {
        Integer original = jdbcTemplate.queryForObject("SELECT status FROM sys_dict WHERE dict_type='cert_status' AND dict_value='1'", Integer.class);
        jdbcTemplate.update("UPDATE sys_dict SET status=0 WHERE dict_type='cert_status' AND dict_value='1'");
        try {
            long before = certificateCount(personAId);
            assertCode(400, request("POST", API, saveBody(personAId), businessToken));
            assertEquals(before, certificateCount(personAId));
            assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
        } finally {
            jdbcTemplate.update("UPDATE sys_dict SET status=? WHERE dict_type='cert_status' AND dict_value='1'", original);
        }
    }

    @Test
    @DisplayName("当前证件可明确转为历史，系统不自动选取另一历史证件")
    void currentCertificateCanBecomeHistory() throws Exception {
        Map<String, Object> body = saveBody(personAId);
        body.put("isCurrent", 0);
        assertSuccess(request("PUT", API + "/" + currentAId, body, businessToken));
        assertEquals(0, currentCount(personAId));
        assertTrue(currentData(personAId, businessToken).isNull());
        assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
        assertEquals(1, currentCount(personBId));
    }

    @Test
    @DisplayName("仅修改发证日期也校验已保存有效期，空日期保持原值")
    void partialIssueDateAndNullUpdatesUseFinalDates() throws Exception {
        Map<String, Object> invalid = saveBody(personAId);
        invalid.put("issueDate", today.plusDays(101).toString());
        assertCode(400, request("PUT", API + "/" + currentAId, invalid, businessToken));
        assertEquals(today.minusDays(100), disabledCertMapper.selectById(currentAId).getIssueDate());
        Map<String, Object> valid = saveBody(personAId);
        valid.put("issueDate", today.minusDays(20).toString());
        valid.put("expireDate", null);
        valid.put("isCurrent", null);
        assertSuccess(request("PUT", API + "/" + currentAId, valid, businessToken));
        DisabledCertPO po = disabledCertMapper.selectById(currentAId);
        assertEquals(today.minusDays(20), po.getIssueDate());
        assertEquals(today.plusDays(100), po.getExpireDate());
        assertEquals(1, po.getIsCurrent());
    }

    @Test
    @DisplayName("新增日期不合法不改变旧当前证件，边界同一天也拒绝")
    void invalidCreationDatesLeaveCurrentUnchanged() throws Exception {
        for (LocalDate expire : List.of(today, today.minusDays(1))) {
            Map<String, Object> body = saveBody(personAId);
            body.put("issueDate", today.toString());
            body.put("expireDate", expire.toString());
            assertCode(400, request("POST", API, body, businessToken));
        }
        assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
        assertEquals(1, currentCount(personAId));
    }

    @Test
    @DisplayName("证件字段长度与实库一致，字符串边界可保存")
    void certificateFieldLengthsMatchSchema() throws Exception {
        Map<String, Integer> lengths = Map.of("certNo", 50, "certType", 20, "issueOrg", 100,
                "disabilityType", 20, "disabilityLevel", 20, "certStatus", 20, "remark", 255);
        for (var field : lengths.entrySet()) {
            Map<String, Object> body = saveBody(personAId);
            body.put(field.getKey(), "长".repeat(field.getValue() + 1));
            assertCode(400, request("POST", API, body, businessToken));
            assertCode(400, request("PUT", API + "/" + currentAId, body, businessToken));
        }
        Map<String, Object> body = saveBody(personAId);
        body.put("certNo", "边".repeat(50));
        body.put("issueOrg", "机".repeat(100));
        body.put("remark", "注".repeat(255));
        body.put("isCurrent", 0);
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        DisabledCertPO saved = disabledCertMapper.selectById(response.get("data").asLong());
        assertEquals(50, saved.getCertNo().length());
        assertEquals(100, saved.getIssueOrg().length());
        assertEquals(255, saved.getRemark().length());
    }

    @Test
    @DisplayName("证件列表可组合证号、字典状态及当前标识精确筛选")
    void certificateListFiltersUseStoredValues() throws Exception {
        DisabledCertPO history = new DisabledCertPO();
        history.setId(historyAId);
        history.setCertStatus("2");
        disabledCertMapper.updateById(history);
        String filters = "&certNo=" + enc("HISTORY-A-" + personAId) + "&certStatus=2&isCurrent=0";
        JsonNode response = request("GET", API + "/list?personId=" + personAId + filters, null, businessToken);
        assertSuccess(response);
        assertEquals(1, response.get("data").size());
        assertEquals(historyAId.longValue(), response.get("data").get(0).get("id").asLong());
        assertCode(400, request("GET", API + "/list?personId=" + personAId + "&isCurrent=2", null, businessToken));
    }

    @Test
    @DisplayName("证件操作与列表继续执行角色及网格校验，字典访问不要求网格")
    void certificateRoleAndMissingGridAccessAreRejected() throws Exception {
        for (String token : List.of(workerToken, noGridToken)) {
            assertCode(403, request("GET", API + "/list?personId=" + personAId, null, token));
            assertCode(403, request("GET", API + "/current?personId=" + personAId, null, token));
            assertCode(403, request("POST", API, saveBody(personAId), token));
            assertCode(403, request("PUT", API + "/" + currentAId, saveBody(personAId), token));
            assertCode(403, request("DELETE", API + "/" + currentAId, null, token));
        }
        assertCode(401, request("GET", API + "/list?personId=" + personAId, null, null));
        assertEquals(1, currentCount(personAId));
    }

    @Test
    @DisplayName("人员与当前标识非法时拒绝写入，不存在记录按原权限契约返回")
    void invalidAndMissingIdentifiersAreRejected() throws Exception {
        assertCode(400, request("POST", API, Map.of(), businessToken));
        assertCode(400, request("POST", API, saveBody(0L), businessToken));
        assertCode(404, request("POST", API, saveBody(Long.MAX_VALUE), adminToken));
        assertCode(403, request("POST", API, saveBody(Long.MAX_VALUE), businessToken));
        for (int flag : List.of(-1, 2)) {
            Map<String, Object> body = saveBody(personAId);
            body.put("isCurrent", flag);
            assertCode(400, request("POST", API, body, businessToken));
        }
        assertCode(404, request("PUT", API + "/9223372036854775807", saveBody(personAId), adminToken));
        assertCode(404, request("DELETE", API + "/9223372036854775807", null, adminToken));
    }

    @Test
    @DisplayName("跨人员重复证号拒绝，同一人员保留相同证号历史记录")
    void certificateNumberDuplicateRuleRemains() throws Exception {
        Map<String, Object> duplicate = saveBody(personAId);
        duplicate.put("certNo", "CURRENT-B-" + personBId);
        assertCode(400, request("POST", API, duplicate, adminToken));
        assertEquals(1, currentCount(personAId));
        duplicate.put("certNo", "CURRENT-A-" + personAId);
        duplicate.put("isCurrent", 0);
        assertSuccess(request("POST", API, duplicate, businessToken));
        assertEquals(1, currentCount(personAId));
    }

    @Test
    @DisplayName("清理旧当前证件后新增失败，事务恢复旧当前标识")
    void failedInsertRollsBackCurrentSwitch() throws Exception {
        String rejected = "DC-FAIL-INSERT-" + personAId;
        jdbcTemplate.execute("ALTER TABLE disabled_cert ADD CONSTRAINT dc_insert_failure CHECK (cert_no <> '" + rejected + "')");
        try {
            long before = certificateCount(personAId);
            Map<String, Object> body = saveBody(personAId);
            body.put("certNo", rejected);
            JsonNode response = request("POST", API, body, businessToken);
            assertCode(500, response);
            assertEquals(before, certificateCount(personAId));
            assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
            assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
            assertEquals(1, currentCount(personAId));
        } finally {
            jdbcTemplate.execute("ALTER TABLE disabled_cert DROP CONSTRAINT dc_insert_failure");
        }
    }

    @Test
    @DisplayName("清理旧当前证件后更新失败，事务恢复两条证件的原值")
    void failedUpdateRollsBackCurrentSwitch() throws Exception {
        String rejected = "DC-FAIL-UPDATE-" + personAId;
        jdbcTemplate.execute("ALTER TABLE disabled_cert ADD CONSTRAINT dc_update_failure CHECK (cert_no <> '" + rejected + "')");
        try {
            Map<String, Object> body = saveBody(personAId);
            body.put("certNo", rejected);
            body.put("isCurrent", 1);
            assertCode(500, request("PUT", API + "/" + historyAId, body, businessToken));
            assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
            assertEquals(0, disabledCertMapper.selectById(historyAId).getIsCurrent());
            assertEquals("HISTORY-A-" + personAId, disabledCertMapper.selectById(historyAId).getCertNo());
            assertEquals(1, currentCount(personAId));
        } finally {
            jdbcTemplate.execute("ALTER TABLE disabled_cert DROP CONSTRAINT dc_update_failure");
        }
    }

    @Test
    @DisplayName("同一人员并发新增当前证件串行加锁，均成功且最终仅一条当前记录")
    void concurrentCurrentCreationIsSerializedByPersonLock() throws Exception {
        Long personId = createPerson("并发新增无证件人员", gridAId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch staged = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
                LoginUser user = new LoginUser(adminUserId, "dc_test_admin", "", true, authorities);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        DisabledCertSaveDTO dto = new DisabledCertSaveDTO();
                        dto.setPersonId(personId);
                        dto.setCertNo("DC-CONCURRENT-A-" + personId);
                        Long id = disabledCertService.create(dto);
                        staged.countDown();
                        await(commit);
                        return id;
                    });
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(staged.await(10, TimeUnit.SECONDS));
            Future<JsonNode> second = executor.submit(() -> {
                secondStarted.countDown();
                Map<String, Object> body = saveBody(personId);
                body.put("certNo", "DC-CONCURRENT-B-" + personId);
                return request("POST", API, body, businessToken);
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            // 首条证件尚未提交，第二请求必须等待同一个人员锁。
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            Long firstId = first.get(10, TimeUnit.SECONDS);
            JsonNode response = second.get(10, TimeUnit.SECONDS);
            assertSuccess(response);
            Long secondId = response.get("data").asLong();
            assertEquals(2, certificateCount(personId));
            assertEquals(1, currentCount(personId));
            assertEquals(0, disabledCertMapper.selectById(firstId).getIsCurrent());
            assertEquals(1, disabledCertMapper.selectById(secondId).getIsCurrent());
            assertEquals(secondId.longValue(), currentData(personId, businessToken).get("id").asLong());
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("异常历史数据中存在两条当前记录时返回409，不自动修复业务数据")
    void duplicateCurrentRowsAreReportedAsConflict() throws Exception {
        Long duplicate = seedCert(personAId, "DC-DUPLICATE-CURRENT-" + personAId, 1);
        assertCode(409, request("GET", API + "/current?personId=" + personAId, null, businessToken));
        assertEquals(1, disabledCertMapper.selectById(currentAId).getIsCurrent());
        assertEquals(1, disabledCertMapper.selectById(duplicate).getIsCurrent());
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

    private long certificateCount(Long personId) {
        return disabledCertMapper.selectCount(new LambdaQueryWrapper<DisabledCertPO>().eq(DisabledCertPO::getPersonId, personId));
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private void assertCode(int code, JsonNode response) {
        assertEquals(code, response.get("code").asInt(), response.toString());
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

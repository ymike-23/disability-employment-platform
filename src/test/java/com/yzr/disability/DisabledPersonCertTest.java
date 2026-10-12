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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 残疾人档案"基础证件信息"集成测试。
 *
 * <p>测试数据写入 H2 内存库（src/test/resources/application.yml），不连接开发库 disability_db。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisabledPersonCertTest {

    private static final String PWD = "Test@123456";

    // 证件状态（与 DisabledPersonService 常量一致）
    private static final String ST_NORMAL = "正常";
    private static final String ST_RENEWING = "换证中";
    private static final String ST_CANCELLED = "注销";
    private static final String ST_LOST = "挂失";

    // 证号
    private static final String CERT_NORMAL = "CERT-N-001";
    private static final String CERT_RENEWING = "CERT-R-002";
    private static final String CERT_CANCELLED = "CERT-C-003";
    private static final String CERT_LOST = "CERT-L-004";
    private static final String CERT_TODAY = "CERT-T-005";
    private static final String CERT_PAST = "CERT-P-006";
    private static final String CERT_REUSE = "CERT-DEL-007";

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

    private final List<Long> createdUserIds = new ArrayList<>();

    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String biz1Token;
    private String biz2Token;

    /** 网格A：无证件信息的历史档案 */
    private Long pNoCert;
    /** 网格A：正常，有效期远期 */
    private Long pNormal;
    /** 网格A：换证中，今天+10 到期 */
    private Long pRenewing;
    /** 网格A：注销，今天+10 到期（应排除出到期预警） */
    private Long pCancelled;
    /** 网格A：挂失，今天+10 到期（应排除出到期预警） */
    private Long pLost;
    /** 网格A：正常，今天到期（闭区间左边界） */
    private Long pExpireToday;
    /** 网格A：昨天已过期（早于今天，不应出现在预警） */
    private Long pExpired;
    /** 网格B：其他网格档案，用于越权测试 */
    private Long pOtherGrid;
    /** 网格A：将被逻辑删除，用于验证证号可复用 */
    private Long pToDelete;

    private LocalDate today;
    private LocalDate in10Days;
    private LocalDate farFuture;

    @BeforeAll
    void setup() throws Exception {
        Long adminRoleId = roleIdOf("ADMIN");
        Long bizRoleId = roleIdOf("BUSINESS");

        Long adminId = createUser("cert_admin", adminRoleId);
        Long biz1Id = createUser("cert_biz1", bizRoleId);
        Long biz2Id = createUser("cert_biz2", bizRoleId);

        gridAId = createGrid("证件测试网格A", biz1Id);
        gridBId = createGrid("证件测试网格B", biz2Id);

        today = LocalDate.now();
        in10Days = today.plusDays(10);
        farFuture = today.plusDays(365);
        LocalDate past = today.minusDays(1);
        LocalDate issueDate = today.minusDays(100);

        // 历史档案：完全没有证件信息（全部为 null）
        pNoCert = createPerson("历史无证件", "110101198001010001", gridAId, null, null, null, null);
        pNormal = createPerson("证正常", "110101198001010002", gridAId, CERT_NORMAL, issueDate, farFuture, ST_NORMAL);
        pRenewing = createPerson("证换证中", "110101198001010003", gridAId, CERT_RENEWING, issueDate, in10Days, ST_RENEWING);
        pCancelled = createPerson("证注销", "110101198001010004", gridAId, CERT_CANCELLED, issueDate, in10Days, ST_CANCELLED);
        pLost = createPerson("证挂失", "110101198001010005", gridAId, CERT_LOST, issueDate, in10Days, ST_LOST);
        pExpireToday = createPerson("今天到期", "110101198001010006", gridAId, CERT_TODAY, issueDate, today, ST_NORMAL);
        pExpired = createPerson("昨天过期", "110101198001010007", gridAId, CERT_PAST, issueDate, past, ST_NORMAL);
        pOtherGrid = createPerson("网格B档案", "110101198001010008", gridBId, "CERT-B-008", issueDate, in10Days, ST_NORMAL);
        pToDelete = createPerson("待删除档案", "110101198001010009", gridAId, CERT_REUSE, issueDate, farFuture, ST_NORMAL);

        adminToken = login("cert_admin");
        biz1Token = login("cert_biz1");
        biz2Token = login("cert_biz2");
    }

    @AfterAll
    void cleanup() {
        // H2 随 JVM 销毁无需清理；仅清理写入 Redis 的测试 Token
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    // ==================== 新增 / 修改 / 详情 ====================

    @Test
    @DisplayName("✅ 新增档案时填写证件信息，并正确落库")
    void createWithCertInfo() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "新增带证件");
        body.put("idCard", "110101198001020001");
        body.put("certNo", "CERT-NEW-100");
        body.put("certIssueDate", today.minusDays(30).toString());
        body.put("certExpireDate", today.plusDays(300).toString());
        body.put("certIssueOrg", "呼和浩特市残疾人联合会");
        body.put("certStatus", ST_NORMAL);

        String resp = post("/api/disabled-person", objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(200, codeOf(resp), "新增应成功");

        Long newId = objectMapper.readTree(resp).get("data").asLong();
        DisabledPersonPO po = disabledPersonMapper.selectById(newId);
        assertEquals("CERT-NEW-100", po.getCertNo(), "证号应落库");
        assertEquals(today.minusDays(30), po.getCertIssueDate(), "发证日期应落库");
        assertEquals(today.plusDays(300), po.getCertExpireDate(), "有效期至应落库");
        assertEquals("呼和浩特市残疾人联合会", po.getCertIssueOrg(), "发证机关应落库");
        assertEquals(ST_NORMAL, po.getCertStatus(), "证件状态应落库");
        assertEquals(gridAId, po.getGridId(), "业务人员的网格应由后端解析");
    }

    @Test
    @DisplayName("✅ 修改档案时维护证件信息")
    void updateCertInfo() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "证正常");
        body.put("certNo", "CERT-UPD-200");
        body.put("certIssueDate", today.minusDays(200).toString());
        body.put("certExpireDate", today.plusDays(200).toString());
        body.put("certIssueOrg", "新城区残联");
        body.put("certStatus", ST_RENEWING);

        String resp = put("/api/disabled-person/" + pNormal, objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(200, codeOf(resp), "修改应成功");

        DisabledPersonPO po = disabledPersonMapper.selectById(pNormal);
        assertEquals("CERT-UPD-200", po.getCertNo());
        assertEquals("新城区残联", po.getCertIssueOrg());
        assertEquals(ST_RENEWING, po.getCertStatus());

        // 还原，避免影响其他用例
        restoreNormalCert();
    }

    @Test
    @DisplayName("✅ 详情接口返回全部证件信息")
    void detailReturnsCertInfo() throws Exception {
        JsonNode data = detail(biz1Token, pRenewing);

        assertEquals(CERT_RENEWING, data.get("certNo").asText(), "应返回证号");
        assertEquals(in10Days.toString(), data.get("certExpireDate").asText(), "应返回有效期至");
        assertEquals(ST_RENEWING, data.get("certStatus").asText(), "应返回证件状态");
        assertFalse(data.get("certIssueDate").isNull(), "应返回发证日期");
    }

    @Test
    @DisplayName("✅ 空证件信息的历史档案可正常查询，证件字段返回 null")
    void archiveWithoutCertStillWorks() throws Exception {
        List<Long> ids = pageIds(biz1Token, "");
        assertTrue(ids.contains(pNoCert), "无证件信息的历史档案应能被查询到");

        JsonNode data = detail(biz1Token, pNoCert);
        assertTrue(data.get("certNo").isNull(), "未录入证件的档案证号应为 null");
        assertTrue(data.get("certStatus").isNull(), "未录入证件的档案状态应为 null");
        assertTrue(data.get("certExpireDate").isNull(), "未录入证件的档案有效期应为 null");
    }

    // ==================== 证号查重 ====================

    @Test
    @DisplayName("🔒 证号与其他档案重复 → 新增被拒绝")
    void certNoDuplicateRejectedOnCreate() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "证号重复");
        body.put("certNo", CERT_RENEWING);

        String resp = post("/api/disabled-person", objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(500, codeOf(resp), "证号重复应被拒绝");
        assertEquals("残疾人证号已存在", messageOf(resp));
    }

    @Test
    @DisplayName("✅ 修改时保留自己的原证号 → 不判为重复")
    void updateKeepOwnCertNoAllowed() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "证正常改名");
        body.put("certNo", CERT_NORMAL);

        String resp = put("/api/disabled-person/" + pNormal, objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(200, codeOf(resp), "修改为自己的原证号不应判重复");

        // 还原名称
        Map<String, Object> restore = new LinkedHashMap<>();
        restore.put("name", "证正常");
        put("/api/disabled-person/" + pNormal, objectMapper.writeValueAsString(restore), biz1Token);
    }

    @Test
    @DisplayName("🔒 修改成他人证号 → 被拒绝")
    void updateToOthersCertNoRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        // 注意：SaveDTO 的 name 是 @NotBlank，修改接口也必须携带姓名（既有设计）
        body.put("name", "证正常");
        body.put("certNo", CERT_CANCELLED);

        String resp = put("/api/disabled-person/" + pNormal, objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(500, codeOf(resp), "改成他人证号应被拒绝");
        assertEquals("残疾人证号已存在", messageOf(resp));

        DisabledPersonPO po = disabledPersonMapper.selectById(pNormal);
        assertEquals(CERT_NORMAL, po.getCertNo(), "失败后证号不应被修改");
    }

    @Test
    @DisplayName("🔒 逻辑删除后，其证号允许复用；未删除档案的证号不可复用")
    void deletedCertNoCanBeReused() throws Exception {
        // 先确认未删除时不可复用
        Map<String, Object> dupBody = new LinkedHashMap<>();
        dupBody.put("name", "复用未删除证号");
        dupBody.put("certNo", CERT_REUSE);
        String dupResp = post("/api/disabled-person", objectMapper.writeValueAsString(dupBody), biz1Token);
        assertEquals(500, codeOf(dupResp), "未删除档案的证号不应被复用");

        // 逻辑删除该档案
        String delResp = delete("/api/disabled-person/" + pToDelete, biz1Token);
        assertEquals(200, codeOf(delResp), "删除应成功");

        // 删除后证号可复用
        String reuseResp = post("/api/disabled-person", objectMapper.writeValueAsString(dupBody), biz1Token);
        assertEquals(200, codeOf(reuseResp), "已逻辑删除档案的证号应允许复用");
    }

    // ==================== 非法输入 ====================

    @Test
    @DisplayName("🔒 非法证件状态 → 400")
    void invalidCertStatusRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "非法状态");
        body.put("certNo", "CERT-BAD-300");
        body.put("certStatus", "失效");

        String resp = post("/api/disabled-person", objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(400, codeOf(resp), "非法证件状态应返回 400");
        assertTrue(messageOf(resp).contains("证件状态不合法"), "提示应说明状态不合法");
    }

    @Test
    @DisplayName("🔒 有效期至早于/等于发证日期 → 400")
    void invalidDateRangeRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "非法日期");
        body.put("certIssueDate", today.toString());
        body.put("certExpireDate", today.minusDays(1).toString());

        String resp = post("/api/disabled-person", objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(400, codeOf(resp), "有效期早于发证日期应返回 400");
        assertEquals("有效期至必须晚于发证日期", messageOf(resp));

        // 边界：有效期等于发证日期同样不允许
        Map<String, Object> equalBody = new LinkedHashMap<>();
        equalBody.put("name", "日期相同");
        equalBody.put("certIssueDate", today.toString());
        equalBody.put("certExpireDate", today.toString());
        String equalResp = post("/api/disabled-person", objectMapper.writeValueAsString(equalBody), biz1Token);
        assertEquals(400, codeOf(equalResp), "有效期等于发证日期也应返回 400");
    }

    @Test
    @DisplayName("✅ 只填发证日期不填有效期 → 允许（证件信息非必填）")
    void partialCertDateAllowed() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "只填发证日期");
        body.put("certIssueDate", today.toString());

        String resp = post("/api/disabled-person", objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(200, codeOf(resp), "只填一个日期应允许新增");
    }

    // ==================== 查询筛选 ====================

    @Test
    @DisplayName("✅ 按证件号精确查询")
    void queryByCertNoExact() throws Exception {
        List<Long> ids = pageIds(adminToken, "&certNo=" + CERT_NORMAL);
        assertEquals(List.of(pNormal), ids, "按证号精确查询应只返回该档案");
    }

    @Test
    @DisplayName("✅ 按证件状态筛选")
    void filterByCertStatus() throws Exception {
        List<Long> ids = pageIds(adminToken, "&certStatus=" + enc(ST_RENEWING));
        assertTrue(ids.contains(pRenewing), "应包含换证中档案");
        assertFalse(ids.contains(pNormal), "不应包含正常档案");
        assertFalse(ids.contains(pCancelled), "不应包含注销档案");
    }

    @Test
    @DisplayName("🔒 查询参数中的非法证件状态 → 400")
    void invalidCertStatusInQueryRejected() throws Exception {
        String resp = get("/api/disabled-person/page?current=1&size=50&certStatus=" + enc("乱写"), adminToken);
        assertEquals(400, codeOf(resp), "查询参数非法状态应返回 400");
    }

    @Test
    @DisplayName("✅ 到期筛选：包含今天到期与换证中，排除已注销/已挂失/已过期/无有效期")
    void expiringSoonFilter() throws Exception {
        List<Long> ids = pageIds(adminToken, "&expiringSoon=true");

        assertTrue(ids.contains(pExpireToday), "今天到期应被预警（闭区间左边界）");
        assertTrue(ids.contains(pRenewing), "换证中且临近到期应被预警");
        assertFalse(ids.contains(pCancelled), "已注销证件不应参与预警");
        assertFalse(ids.contains(pLost), "已挂失证件不应参与预警");
        assertFalse(ids.contains(pExpired), "已过期（早于今天）不应出现在预警");
        assertFalse(ids.contains(pNormal), "有效期远期不应被预警");
        assertFalse(ids.contains(pNoCert), "无有效期档案不应被预警");
    }

    @Test
    @DisplayName("✅ 到期筛选：预警天数边界生效（默认30天 vs 指定5天）")
    void expiringSoonDaysBoundary() throws Exception {
        // 默认 30 天：10 天后到期应命中
        List<Long> defaultIds = pageIds(adminToken, "&expiringSoon=true");
        assertTrue(defaultIds.contains(pRenewing), "默认 30 天应命中 10 天后到期");

        // 指定 5 天：10 天后到期不命中；今天到期仍命中
        List<Long> ids5 = pageIds(adminToken, "&expiringSoon=true&expireWithinDays=5");
        assertFalse(ids5.contains(pRenewing), "5 天预警不应命中 10 天后到期");
        assertTrue(ids5.contains(pExpireToday), "5 天预警应命中今天到期");
    }

    // ==================== 权限回归 ====================

    @Test
    @DisplayName("🔒 ADMIN 与 BUSINESS 既有权限规则仍然生效")
    void permissionRulesStillApply() throws Exception {
        List<Long> adminIds = pageIds(adminToken, "");
        assertTrue(adminIds.contains(pOtherGrid), "ADMIN 应能看到网格B档案");

        List<Long> biz1Ids = pageIds(biz1Token, "");
        assertTrue(biz1Ids.contains(pNormal), "BUSINESS 应能看到本网格档案");
        assertFalse(biz1Ids.contains(pOtherGrid), "BUSINESS 不应看到其他网格档案");
    }

    @Test
    @DisplayName("🔒 跨网格查看/修改/删除证件信息仍被拒绝（403）")
    void crossGridCertAccessDenied() throws Exception {
        assertEquals(403, codeOf(get("/api/disabled-person/" + pOtherGrid, biz1Token)),
                "跨网格查看详情应 403");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "网格B档案");
        body.put("certNo", "CERT-HACK-999");
        body.put("certStatus", ST_NORMAL);
        String updResp = put("/api/disabled-person/" + pOtherGrid, objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(403, codeOf(updResp), "跨网格修改证件应 403");

        DisabledPersonPO po = disabledPersonMapper.selectById(pOtherGrid);
        assertEquals("CERT-B-008", po.getCertNo(), "越权修改后证号不应变化");

        assertEquals(403, codeOf(delete("/api/disabled-person/" + pOtherGrid, biz1Token)),
                "跨网格删除应 403");
        assertNotNull(disabledPersonMapper.selectById(pOtherGrid), "越权删除不应生效");
    }

    @Test
    @DisplayName("🔒 未登录访问证件相关查询 → 401")
    void anonymousAccessReturns401() throws Exception {
        assertEquals(401, getRaw("/api/disabled-person/page?certNo=" + CERT_NORMAL, null));
        assertEquals(401, getRaw("/api/disabled-person/" + pNormal, null));
    }

    // ==================== 辅助方法 ====================

    /** 还原 pNormal 的证件信息，避免用例间相互干扰 */
    private void restoreNormalCert() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "证正常");
        body.put("certNo", CERT_NORMAL);
        body.put("certIssueDate", today.minusDays(100).toString());
        body.put("certExpireDate", farFuture.toString());
        body.put("certStatus", ST_NORMAL);
        String resp = put("/api/disabled-person/" + pNormal, objectMapper.writeValueAsString(body), biz1Token);
        assertEquals(200, codeOf(resp), "还原 pNormal 证件信息应成功");
    }

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

    private Long createPerson(String name, String idCard, Long gridId,
                              String certNo, LocalDate issueDate, LocalDate expireDate, String certStatus) {
        DisabledPersonPO po = new DisabledPersonPO();
        po.setName(name);
        po.setIdCard(idCard);
        po.setGridId(gridId);
        po.setStatus(1);
        po.setDeleted(0);
        po.setCertNo(certNo);
        po.setCertIssueDate(issueDate);
        po.setCertExpireDate(expireDate);
        po.setCertStatus(certStatus);
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

    /** 查询分页并返回 ID 列表 */
    private List<Long> pageIds(String token, String extraQuery) throws Exception {
        String resp = get("/api/disabled-person/page?current=1&size=100" + extraQuery, token);
        assertEquals(200, codeOf(resp), "分页查询应成功: " + extraQuery);
        JsonNode data = objectMapper.readTree(resp).get("data");
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : data.get("records")) {
            ids.add(node.get("id").asLong());
        }
        return ids;
    }

    private JsonNode detail(String token, Long id) throws Exception {
        String resp = get("/api/disabled-person/" + id, token);
        assertEquals(200, codeOf(resp), "详情查询应成功");
        return objectMapper.readTree(resp).get("data");
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

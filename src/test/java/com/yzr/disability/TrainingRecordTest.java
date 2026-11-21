package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.po.TrainingRecordPO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.mapper.TrainingRecordMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import com.yzr.disability.service.TrainingRecordService;
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
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 培训记录接口、报名规则及网格权限集成测试，仅使用 H2 测试库。
 *
 * <p>报名只校验业务人员对所属人员档案的网格权限，不再要求计划与用户网格对应；
 * 计划必须已发布且处于报名中；同一人员与计划只允许一条记录。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingRecordTest {

    private static final String API = "/api/training-record";
    private static final String PWD = "Test@123456";

    @LocalServerPort
    private int port;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private TrainingRecordMapper trainingRecordMapper;
    @Autowired private TrainingPlanMapper trainingPlanMapper;
    @Autowired private DisabledPersonMapper disabledPersonMapper;
    @Autowired private SysDictMapper sysDictMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private SysUserRoleMapper sysUserRoleMapper;
    @Autowired private SysRoleMapper sysRoleMapper;
    @Autowired private SysGridMapper sysGridMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long adminUserId;
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private Long personAId;
    private Long personBId;
    private Long planAId;
    private Long planBId;
    private Long planCId;
    private Long enrolledAId;
    private Long enrolledBId;
    private Long cancelledAId;
    private Long completedAId;

    @BeforeAll
    void setupUsers() throws Exception {
        adminUserId = createUser("tr_test_admin", "ADMIN");
        Long businessUserId = createUser("tr_test_business_a", "BUSINESS");
        Long businessBUserId = createUser("tr_test_business_b", "BUSINESS");
        createUser("tr_test_no_grid", "BUSINESS");
        Long multiUserId = createUser("tr_test_multi_grid", "BUSINESS");
        Long workerUserId = createUser("tr_test_worker", "GRID_WORKER");
        gridAId = createGrid("培训记录测试网格A", businessUserId);
        gridBId = createGrid("培训记录测试网格B", businessBUserId);
        createGrid("培训记录测试多网格1", multiUserId);
        createGrid("培训记录测试多网格2", multiUserId);
        createGrid("培训记录测试非业务角色网格", workerUserId);
        adminToken = login("tr_test_admin");
        businessToken = login("tr_test_business_a");
        noGridToken = login("tr_test_no_grid");
        multiGridToken = login("tr_test_multi_grid");
        workerToken = login("tr_test_worker");
    }

    @BeforeEach
    void setupRecords() {
        personAId = createPerson("培训记录测试人员A", gridAId);
        personBId = createPerson("培训记录测试人员B", gridBId);
        planAId = createPlan(20);
        planBId = createPlan(20);
        planCId = createPlan(20);
        // 人员与计划的组合全局唯一，直接播种即受 uk_person_plan 约束保护。
        enrolledAId = seedRecord(personAId, planAId, TrainingRecordService.STATUS_ENROLLED);
        enrolledBId = seedRecord(personBId, planAId, TrainingRecordService.STATUS_ENROLLED);
        cancelledAId = seedRecord(personAId, planBId, TrainingRecordService.STATUS_CANCELLED);
        completedAId = seedRecord(personAId, planCId, TrainingRecordService.STATUS_COMPLETED);
        // planA=2 人、planB=0 人（已取消不占名额）、planC=1 人
    }

    @AfterAll
    void cleanupTokens() {
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("新增培训记录成功，报名人数加1，流程状态与结业结果独立存储")
    void createRecord() throws Exception {
        Long personId = createPerson("新增报名人员", gridAId);
        Map<String, Object> body = saveBody(personId, planAId);
        body.put("remark", "新增报名");
        JsonNode response = request("POST", API, body, businessToken);
        assertSuccess(response);
        Long id = response.get("data").asLong();
        TrainingRecordPO record = trainingRecordMapper.selectById(id);
        assertNotNull(record);
        assertEquals(personId, record.getPersonId());
        assertEquals(planAId, record.getPlanId());
        assertEquals("1", record.getStatus());
        assertNull(record.getCertificateNo());
        assertNull(record.getScore());
        assertNotNull(record.getEnrollDate());
        assertNotNull(record.getCreateTime());
        assertNotNull(record.getUpdateTime());
        assertEquals(0, record.getDeleted());
        assertEquals("1", jdbcTemplate.queryForObject("SELECT status FROM training_record WHERE id = ?", String.class, id));
        assertNull(jdbcTemplate.queryForObject("SELECT result FROM training_record WHERE id = ?", String.class, id));
        assertNull(jdbcTemplate.queryForObject("SELECT cert_no FROM training_record WHERE id = ?", String.class, id));
        assertEquals(3, enrolled(planAId));
    }

    @Test
    @DisplayName("同一人员重复报名同一计划返回409，已取消记录也不能重建")
    void duplicateEnrollmentIsRejected() throws Exception {
        long before = recordCount(planAId);
        JsonNode duplicated = request("POST", API, saveBody(personAId, planAId), businessToken);
        assertEquals(409, duplicated.get("code").asInt());
        assertEquals("该人员已报名该培训计划", duplicated.get("message").asText());

        // 已取消（status=5）的记录同样占位，恢复报名应修改原记录而不是新增。
        long beforePlanB = recordCount(planBId);
        JsonNode cancelled = request("POST", API, saveBody(personAId, planBId), adminToken);
        assertEquals(409, cancelled.get("code").asInt());
        assertEquals(before, recordCount(planAId));
        assertEquals(beforePlanB, recordCount(planBId));
        assertEquals(2, enrolled(planAId));
        assertEquals(0, enrolled(planBId));
    }

    @Test
    @DisplayName("不同人员可以报名同一计划，人数分别累加")
    void differentPersonsCanEnrollSamePlan() throws Exception {
        Long first = createPerson("同计划人员1", gridAId);
        Long second = createPerson("同计划人员2", gridAId);
        assertSuccess(request("POST", API, saveBody(first, planCId), businessToken));
        assertSuccess(request("POST", API, saveBody(second, planCId), businessToken));
        assertEquals(3, enrolled(planCId));
        assertEquals(3, recordCount(planCId));
    }

    @Test
    @DisplayName("逻辑删除后仍不能为同一人员与计划重新创建报名记录")
    void deletedEnrollmentCannotBeRecreated() throws Exception {
        Long personId = createPerson("删除后重报人员", gridAId);
        JsonNode created = request("POST", API, saveBody(personId, planCId), businessToken);
        assertSuccess(created);
        Long id = created.get("data").asLong();
        assertEquals(2, enrolled(planCId));
        assertSuccess(request("DELETE", API + "/" + id, null, businessToken));
        assertEquals(1, enrolled(planCId));

        // 唯一约束包含已逻辑删除的记录，重建返回 409。
        JsonNode recreated = request("POST", API, saveBody(personId, planCId), businessToken);
        assertEquals(409, recreated.get("code").asInt());
        assertEquals(1, enrolled(planCId));
        assertEquals(1, recordCount(planCId));
    }

    @Test
    @DisplayName("未发布、未处于报名中、已取消或已满员的计划不能新增报名")
    void planStateControlsEnrollment() throws Exception {
        Long personId = createPerson("计划状态校验人员", gridAId);

        // 即使数据库仍为报名中，实际开始日已到也不能报名。
        jdbcTemplate.update("UPDATE training_plan SET status = ?, start_date = ? WHERE id = ?",
                TrainingPlanService.STATUS_ENROLLING, LocalDate.now(), planBId);
        JsonNode notEnrolling = request("POST", API, saveBody(personId, planBId), businessToken);
        assertEquals(409, notEnrolling.get("code").asInt());

        // 已发布但计划已取消。
        jdbcTemplate.update("UPDATE training_plan SET status = ?, publish_status = ? WHERE id = ?",
                TrainingPlanService.STATUS_CANCELLED, TrainingPlanService.PUBLISH_PUBLISHED, planBId);
        assertEquals(409, request("POST", API, saveBody(personId, planBId), businessToken).get("code").asInt());

        // 未发布（草稿）计划即使处于报名中也不能报名。
        jdbcTemplate.update("UPDATE training_plan SET status = ?, publish_status = ? WHERE id = ?",
                TrainingPlanService.STATUS_ENROLLING, TrainingPlanService.PUBLISH_DRAFT, planBId);
        JsonNode draft = request("POST", API, saveBody(personId, planBId), businessToken);
        assertEquals(409, draft.get("code").asInt());
        assertEquals("培训计划未发布，不能报名", draft.get("message").asText());

        // 已发布且报名中，但名额已满。
        jdbcTemplate.update("UPDATE training_plan SET status = ?, publish_status = ?,"
                + " capacity = enrolled_count WHERE id = ?", TrainingPlanService.STATUS_ENROLLING,
                TrainingPlanService.PUBLISH_PUBLISHED, planAId);
        JsonNode full = request("POST", API, saveBody(personId, planAId), businessToken);
        assertEquals(409, full.get("code").asInt());
        assertEquals(2, enrolled(planAId));
        // planB 仅有播种的已取消记录，上述失败请求没有新增任何记录。
        assertEquals(1, recordCount(planBId));
    }

    @Test
    @DisplayName("报名、取消、恢复和删除后 enrolled_count 正确变化")
    void enrolledCountFollowsRecordLifecycle() throws Exception {
        Long personId = createPerson("名额变化人员", gridAId);
        JsonNode created = request("POST", API, saveBody(personId, planBId), businessToken);
        assertSuccess(created);
        Long id = created.get("data").asLong();
        assertEquals(1, enrolled(planBId));

        Map<String, Object> cancel = saveBody(personId, planBId);
        cancel.put("status", TrainingRecordService.STATUS_CANCELLED);
        assertSuccess(request("PUT", API + "/" + id, cancel, businessToken));
        assertEquals(0, enrolled(planBId));

        Map<String, Object> restore = saveBody(personId, planBId);
        restore.put("status", TrainingRecordService.STATUS_ENROLLED);
        assertSuccess(request("PUT", API + "/" + id, restore, businessToken));
        assertEquals(1, enrolled(planBId));

        assertSuccess(request("DELETE", API + "/" + id, null, businessToken));
        assertEquals(0, enrolled(planBId));
    }

    @Test
    @DisplayName("培训计划满额时禁止新增，记录和人数均保持不变")
    void capacityPreventsEnrollment() throws Exception {
        Long personId = createPerson("满额校验人员", gridAId);
        jdbcTemplate.update("UPDATE training_plan SET capacity = enrolled_count WHERE id = ?", planAId);
        long before = recordCount(planAId);
        assertEquals(409, request("POST", API, saveBody(personId, planAId), businessToken).get("code").asInt());
        assertEquals(before, recordCount(planAId));
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("人员和计划分页返回正确总数、页数及不同页记录")
    void pageRecords() throws Exception {
        String query = "personId=" + personAId;
        JsonNode first = pageData(query + "&current=1&size=1", businessToken);
        JsonNode second = pageData(query + "&current=2&size=1", businessToken);
        assertEquals(3, first.get("total").asLong());
        assertEquals(3, first.get("pages").asLong());
        assertEquals(1, first.get("records").size());
        assertNotEquals(first.get("records").get(0).get("id").asLong(),
                second.get("records").get(0).get("id").asLong());
        assertEquals(List.of(enrolledAId), idsOf(pageData(query + "&planId=" + planAId, businessToken)));
        assertEquals(List.of(cancelledAId), idsOf(pageData(query + "&planId=" + planBId, businessToken)));
        assertEquals(List.of(completedAId), idsOf(pageData(query + "&planId=" + planCId, adminToken)));
    }

    @Test
    @DisplayName("查询详情返回字典值和证书编号，不暴露逻辑删除字段")
    void detailRecord() throws Exception {
        JsonNode response = request("GET", API + "/" + enrolledAId, null, businessToken);
        assertSuccess(response);
        JsonNode data = response.get("data");
        assertEquals(personAId.longValue(), data.get("personId").asLong());
        assertEquals(planAId.longValue(), data.get("planId").asLong());
        assertEquals("1", data.get("status").asText());
        assertTrue(data.get("certificateNo").isNull());
        assertEquals("2026-10-13", data.get("enrollDate").asText());
        assertFalse(data.has("deleted"));
    }

    @Test
    @DisplayName("修改成功，可选字段保留，有效状态之间修改不改变报名人数")
    void updateRecord() throws Exception {
        Map<String, Object> body = saveBody(personAId, planAId);
        body.put("status", "2");
        body.put("remark", "修改报名备注");
        assertSuccess(request("PUT", API + "/" + enrolledAId, body, businessToken));
        TrainingRecordPO record = trainingRecordMapper.selectById(enrolledAId);
        assertEquals("2", record.getStatus());
        assertNull(record.getScore());
        assertNull(record.getCertificateNo());
        assertEquals("修改报名备注", record.getRemark());
        assertEquals(personAId, record.getPersonId());
        assertEquals(planAId, record.getPlanId());
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("修改报名状态不覆盖考核结果，已登记成果禁止取消和删除")
    void lifecyclePreservesTrainingResult() throws Exception {
        jdbcTemplate.update("UPDATE training_record SET result = ? WHERE id = ?", "合格", enrolledAId);
        Map<String, Object> body = saveBody(personAId, planAId);
        body.put("status", TrainingRecordService.STATUS_IN_TRAINING);
        assertSuccess(request("PUT", API + "/" + enrolledAId, body, businessToken));
        assertEquals(TrainingRecordService.STATUS_IN_TRAINING,
                jdbcTemplate.queryForObject("SELECT status FROM training_record WHERE id = ?", String.class, enrolledAId));
        assertEquals("合格", jdbcTemplate.queryForObject(
                "SELECT result FROM training_record WHERE id = ?", String.class, enrolledAId));
        assertEquals(2, enrolled(planAId));

        body.put("status", TrainingRecordService.STATUS_CANCELLED);
        assertEquals(409, request("PUT", API + "/" + enrolledAId, body, businessToken).get("code").asInt());
        assertEquals(TrainingRecordService.STATUS_IN_TRAINING, trainingRecordMapper.selectById(enrolledAId).getStatus());
        assertEquals("合格", jdbcTemplate.queryForObject(
                "SELECT result FROM training_record WHERE id = ?", String.class, enrolledAId));
        assertEquals(2, enrolled(planAId));

        assertEquals(409, request("DELETE", API + "/" + enrolledAId, null, businessToken).get("code").asInt());
        assertNotNull(trainingRecordMapper.selectById(enrolledAId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_record WHERE id = ?", Integer.class, enrolledAId));
        assertEquals("合格", jdbcTemplate.queryForObject(
                "SELECT result FROM training_record WHERE id = ?", String.class, enrolledAId));
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("有效记录逻辑删除并恢复人数，重复删除不再减人数")
    void deleteRecordRestoresCapacity() throws Exception {
        assertSuccess(request("DELETE", API + "/" + enrolledAId, null, businessToken));
        assertNull(trainingRecordMapper.selectById(enrolledAId));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_record WHERE id = ?", Integer.class, enrolledAId));
        assertEquals(1, enrolled(planAId));
        assertFalse(idsOf(pageData("personId=" + personAId, businessToken)).contains(enrolledAId));
        assertEquals(404, request("GET", API + "/" + enrolledAId, null, adminToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + enrolledAId, null, businessToken).get("code").asInt());
        assertEquals(1, enrolled(planAId));
    }

    @Test
    @DisplayName("ADMIN 可以跨网格查询、新增、修改、删除培训记录")
    void adminCanOperateAcrossGrids() throws Exception {
        assertSuccess(request("GET", API + "/" + enrolledBId, null, adminToken));
        assertEquals(List.of(enrolledBId), idsOf(pageData("personId=" + personBId, adminToken)));
        Map<String, Object> body = saveBody(personBId, planBId);
        JsonNode create = request("POST", API, body, adminToken);
        assertSuccess(create);
        Long id = create.get("data").asLong();
        assertEquals(1, enrolled(planBId));
        body.put("remark", "管理员更新报名备注");
        assertSuccess(request("PUT", API + "/" + id, body, adminToken));
        assertEquals("管理员更新报名备注", trainingRecordMapper.selectById(id).getRemark());
        assertSuccess(request("DELETE", API + "/" + id, null, adminToken));
        assertEquals(0, enrolled(planBId));
    }

    @Test
    @DisplayName("BUSINESS 按真实人员归属授权，伪造personId不能跨网格操作")
    void businessCannotBypassGridPermissions() throws Exception {
        JsonNode page = pageData("planId=" + planAId, businessToken);
        assertEquals(1, page.get("total").asLong());
        assertFalse(idsOf(page).contains(enrolledBId));
        assertEquals(403, request("GET", API + "/page?personId=" + personBId, null, businessToken).get("code").asInt());
        assertEquals(403, request("GET", API + "/" + enrolledBId + "?personId=" + personAId,
                null, businessToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(personBId, planBId), businessToken).get("code").asInt());
        Map<String, Object> forged = saveBody(personAId, planAId);
        forged.put("score", new BigDecimal("99.00"));
        assertEquals(403, request("PUT", API + "/" + enrolledBId, forged, businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + enrolledBId, null, businessToken).get("code").asInt());
        assertEquals(personBId, trainingRecordMapper.selectById(enrolledBId).getPersonId());
        assertNull(trainingRecordMapper.selectById(enrolledBId).getScore());
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("不存在或已删除的人员不能新增培训记录")
    void invalidPersonIsRejected() throws Exception {
        long before = recordCount(planBId);
        assertEquals(404, request("POST", API, saveBody(Long.MAX_VALUE, planBId), adminToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(Long.MAX_VALUE, planBId), businessToken).get("code").asInt());
        disabledPersonMapper.deleteById(personAId);
        assertEquals(404, request("POST", API, saveBody(personAId, planBId), adminToken).get("code").asInt());
        assertEquals(before, recordCount(planBId));
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("不存在或已删除的计划不能报名，不影响人数及记录")
    void invalidPlanIsRejected() throws Exception {
        long before = recordCount(planAId);
        assertEquals(404, request("POST", API, saveBody(personAId, Long.MAX_VALUE), businessToken).get("code").asInt());
        trainingPlanMapper.deleteById(planAId);
        assertEquals(404, request("POST", API, saveBody(personAId, planAId), adminToken).get("code").asInt());
        assertEquals(before, recordCount(planAId));
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("不存在的记录不能查询、修改或删除，越权与不存在统一403")
    void missingRecordIsRejected() throws Exception {
        String missing = API + "/" + Long.MAX_VALUE;
        assertEquals(404, request("GET", missing, null, adminToken).get("code").asInt());
        assertEquals(404, request("PUT", missing, saveBody(personAId, planAId), adminToken).get("code").asInt());
        assertEquals(404, request("DELETE", missing, null, adminToken).get("code").asInt());
        JsonNode response = request("GET", missing, null, businessToken);
        JsonNode forbidden = request("GET", API + "/" + enrolledBId, null, businessToken);
        assertEquals(403, response.get("code").asInt());
        assertEquals(response.get("message").asText(), forbidden.get("message").asText());
        assertEquals(403, request("PUT", missing, saveBody(personAId, planAId), businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", missing, null, businessToken).get("code").asInt());
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("不能通过修改人员或计划转移报名归属")
    void recordAssociationCannotChange() throws Exception {
        assertEquals(400, request("PUT", API + "/" + enrolledAId,
                saveBody(personBId, planAId), adminToken).get("code").asInt());
        assertEquals(400, request("PUT", API + "/" + enrolledAId,
                saveBody(personAId, planBId), businessToken).get("code").asInt());
        assertEquals(personAId, trainingRecordMapper.selectById(enrolledAId).getPersonId());
        assertEquals(planAId, trainingRecordMapper.selectById(enrolledAId).getPlanId());
        assertEquals(2, enrolled(planAId));
        assertEquals(1, enrolled(planCId));
    }

    @Test
    @DisplayName("取消释放名额，重复取消不重复减人数，恢复报名重新占用名额")
    void cancellationAndRestorationMaintainCounts() throws Exception {
        Map<String, Object> body = saveBody(personAId, planBId);
        body.put("status", TrainingRecordService.STATUS_CANCELLED);
        assertEquals(409, request("PUT", API + "/" + cancelledAId, body, businessToken).get("code").asInt());
        assertEquals(0, enrolled(planBId));
        assertEquals(409, request("PUT", API + "/" + cancelledAId, body, businessToken).get("code").asInt());
        assertEquals(0, enrolled(planBId));

        body.put("status", TrainingRecordService.STATUS_IN_TRAINING);
        assertSuccess(request("PUT", API + "/" + cancelledAId, body, businessToken));
        assertEquals(1, enrolled(planBId));
        assertSuccess(request("PUT", API + "/" + cancelledAId, body, businessToken));
        assertEquals(1, enrolled(planBId));
        assertEquals("2", trainingRecordMapper.selectById(cancelledAId).getStatus());
    }

    @Test
    @DisplayName("已取消记录逻辑删除不再次恢复人数，新增不能直接使用状态5")
    void cancelledRecordDoesNotConsumeCapacity() throws Exception {
        long before = recordCount(planBId);
        // 状态校验早于重复报名校验，因此可复用已存在的 (personA, planB) 组合。
        Map<String, Object> body = saveBody(personAId, planBId);
        body.put("status", TrainingRecordService.STATUS_CANCELLED);
        assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        assertEquals(before, recordCount(planBId));
        assertSuccess(request("DELETE", API + "/" + cancelledAId, null, businessToken));
        assertNull(trainingRecordMapper.selectById(cancelledAId));
        assertEquals(0, enrolled(planBId));
    }

    @Test
    @DisplayName("计划满额时不能恢复取消记录，状态与人数保持不变")
    void restorationRequiresCapacity() throws Exception {
        jdbcTemplate.update("UPDATE training_plan SET capacity = 1, enrolled_count = 1 WHERE id = ?", planBId);
        Map<String, Object> body = saveBody(personAId, planBId);
        body.put("status", TrainingRecordService.STATUS_ENROLLED);
        assertEquals(409, request("PUT", API + "/" + cancelledAId, body, businessToken).get("code").asInt());
        assertEquals("5", trainingRecordMapper.selectById(cancelledAId).getStatus());
        assertEquals(1, enrolled(planBId));
    }

    @Test
    @DisplayName("状态只接受启用字典值，拒绝中文标签和不存在的值")
    void dictionaryStatusIsValidated() throws Exception {
        Long personId = createPerson("字典校验人员", gridAId);
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, "training_record_status").eq(SysDictPO::getDictValue, "2");
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        assertNotNull(dict);
        for (String status : List.of("0", "999", dict.getDictLabel())) {
            Map<String, Object> create = saveBody(personId, planBId);
            create.put("status", status);
            assertEquals(400, request("POST", API, create, businessToken).get("code").asInt());
            // 修改沿用记录真实归属，确保命中状态校验而非归属校验。
            Map<String, Object> update = saveBody(personAId, planBId);
            update.put("status", status);
            assertEquals(400, request("PUT", API + "/" + cancelledAId, update, businessToken).get("code").asInt());
        }
        assertEquals(400, request("GET", API + "/page?status="
                + URLEncoder.encode(dict.getDictLabel(), StandardCharsets.UTF_8), null, adminToken)
                .get("code").asInt());
        Integer originalStatus = dict.getStatus();
        try {
            dict.setStatus(0);
            sysDictMapper.updateById(dict);
            Map<String, Object> create = saveBody(personId, planBId);
            create.put("status", "2");
            assertEquals(400, request("POST", API, create, businessToken).get("code").asInt());
            Map<String, Object> update = saveBody(personAId, planBId);
            update.put("status", "2");
            assertEquals(400, request("PUT", API + "/" + cancelledAId, update, businessToken).get("code").asInt());
        } finally {
            dict.setStatus(originalStatus);
            sysDictMapper.updateById(dict);
        }
        assertEquals("1", trainingRecordMapper.selectById(enrolledAId).getStatus());
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("无网格和多网格均不能操作记录，多网格分页返回403")
    void missingOrMultipleGridsAreRestricted() throws Exception {
        assertEquals(0, pageData("planId=" + planAId, noGridToken).get("total").asLong());
        JsonNode multi = request("GET", API + "/page", null, multiGridToken);
        assertEquals(403, multi.get("code").asInt());
        assertEquals(GridScopeService.MULTI_GRID_MESSAGE, multi.get("message").asText());
        for (String token : List.of(noGridToken, multiGridToken)) {
            assertEquals(403, request("POST", API, saveBody(personAId, planAId), token).get("code").asInt());
            assertEquals(403, request("GET", API + "/" + enrolledAId, null, token).get("code").asInt());
            assertEquals(403, request("PUT", API + "/" + enrolledAId, saveBody(personAId, planAId), token).get("code").asInt());
            assertEquals(403, request("DELETE", API + "/" + enrolledAId, null, token).get("code").asInt());
        }
        assertEquals(2, enrolled(planAId));
    }

    @Test
    @DisplayName("未登录及无ADMIN/BUSINESS角色时拒绝访问")
    void authenticationAndRolesAreRequired() throws Exception {
        assertEquals(401, request("GET", API + "/page", null, null).get("code").asInt());
        assertEquals(403, request("GET", API + "/page", null, workerToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(personAId, planAId), workerToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + enrolledAId, saveBody(personAId, planAId), workerToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + enrolledAId, null, workerToken).get("code").asInt());
    }

    @Test
    @DisplayName("人员和计划必填，字段长度、成绩精度及分页参数校验生效")
    void invalidInputIsRejected() throws Exception {
        Long personId = createPerson("参数校验人员", gridAId);
        long before = recordCount(planBId);
        for (String field : List.of("personId", "planId")) {
            Map<String, Object> body = saveBody(personId, planBId);
            body.remove(field);
            assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        }
        Map<String, Object> body = saveBody(personId, planBId);
        body.put("certificateNo", "X".repeat(51));
        assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        body.remove("certificateNo");
        body.put("score", new BigDecimal("1000.001"));
        assertEquals(400, request("POST", API, body, businessToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?size=101", null, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?personId=0", null, adminToken).get("code").asInt());
        assertEquals(before, recordCount(planBId));
        assertEquals(0, enrolled(planBId));
    }

    @Test
    @DisplayName("并发报名只有剩余容量对应数量成功，计数与记录一致")
    void concurrentEnrollmentsDoNotExceedCapacity() throws Exception {
        Long planId = createPlan(2);
        List<Callable<JsonNode>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Long personId = createPerson("并发报名人员" + i, gridAId);
            requests.add(() -> request("POST", API, saveBody(personId, planId), businessToken));
        }
        List<JsonNode> responses = parallelRequests(requests);
        long succeeded = responses.stream().filter(response -> response.get("code").asInt() == 200).count();
        long rejected = responses.stream().filter(response -> response.get("code").asInt() == 409).count();
        assertEquals(2, succeeded, responses.toString());
        assertEquals(6, rejected, responses.toString());
        assertEquals(2, enrolled(planId));
        assertEquals(2, recordCount(planId));
    }

    @Test
    @DisplayName("同一人员并发报名同一计划只成功一次，重复请求返回409")
    void concurrentDuplicateEnrollmentsAreRejected() throws Exception {
        Long planId = createPlan(20);
        Long personId = createPerson("同一人员并发报名", gridAId);
        List<Callable<JsonNode>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(() -> request("POST", API, saveBody(personId, planId), businessToken));
        }
        List<JsonNode> responses = parallelRequests(requests);
        assertEquals(1, responses.stream().filter(response -> response.get("code").asInt() == 200).count(),
                responses.toString());
        assertEquals(7, responses.stream().filter(response -> response.get("code").asInt() == 409).count(),
                responses.toString());
        assertEquals(1, enrolled(planId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_record WHERE person_id = ? AND plan_id = ?", Long.class, personId, planId));
    }

    @Test
    @DisplayName("并发删除同一记录只成功一次，名额只恢复一次")
    void concurrentDeletesReleaseOneSeat() throws Exception {
        List<JsonNode> responses = parallelRequests(List.of(
                () -> request("DELETE", API + "/" + enrolledAId, null, businessToken),
                () -> request("DELETE", API + "/" + enrolledAId, null, businessToken)));
        assertEquals(1, responses.stream().filter(response -> response.get("code").asInt() == 200).count());
        assertEquals(1, responses.stream().filter(response -> response.get("code").asInt() == 403).count());
        assertNull(trainingRecordMapper.selectById(enrolledAId));
        assertEquals(1, enrolled(planAId));
    }

    @Test
    @DisplayName("计数更新发生数据库异常时，新增记录也回滚")
    void failedIncrementRollsBackInsertion() throws Exception {
        Long planId = createPlan(2);
        Long personId = createPerson("回滚校验人员", gridAId);
        // 仅在 H2 中注入计数写入失败，不改生产表或业务代码。
        jdbcTemplate.execute("ALTER TABLE training_plan ADD CONSTRAINT tr_record_counter_guard"
                + " CHECK (id <> " + planId + " OR enrolled_count = 0)");
        try {
            JsonNode response = request("POST", API, saveBody(personId, planId), businessToken);
            assertEquals(500, response.get("code").asInt());
            assertEquals(0, recordCount(planId));
            assertEquals(0, enrolled(planId));
        } finally {
            jdbcTemplate.execute("ALTER TABLE training_plan DROP CONSTRAINT tr_record_counter_guard");
        }
    }

    @Test
    @DisplayName("计数异常不能减为负数，取消和逻辑删除均回滚")
    void failedDecrementRollsBackRecordMutation() throws Exception {
        jdbcTemplate.update("UPDATE training_plan SET enrolled_count = 0 WHERE id = ?", planAId);
        Map<String, Object> body = saveBody(personAId, planAId);
        body.put("status", TrainingRecordService.STATUS_CANCELLED);
        body.put("remark", "应回滚的变更");
        assertEquals(409, request("PUT", API + "/" + enrolledAId, body, businessToken).get("code").asInt());
        TrainingRecordPO record = trainingRecordMapper.selectById(enrolledAId);
        assertEquals("1", record.getStatus());
        assertEquals("记录测试备注", record.getRemark());
        assertEquals(409, request("DELETE", API + "/" + enrolledAId, null, businessToken).get("code").asInt());
        assertNotNull(trainingRecordMapper.selectById(enrolledAId));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_record WHERE id = ?", Integer.class, enrolledAId));
        assertEquals(0, enrolled(planAId));
    }

    @Test
    @DisplayName("计划已逻辑删除时，历史有效记录仍可删除并恢复计数")
    void deletedPlanCanReleaseHistoricalEnrollment() throws Exception {
        trainingPlanMapper.deleteById(planAId);
        assertSuccess(request("DELETE", API + "/" + enrolledAId, null, businessToken));
        assertEquals(1, enrolled(planAId));
        assertNull(trainingPlanMapper.selectById(planAId));
        assertNull(trainingRecordMapper.selectById(enrolledAId));
        // 已删除的计划不能再新增报名，但不影响历史记录的名额释放。
        Long personId = createPerson("历史计划报名人员", gridAId);
        assertEquals(404, request("POST", API, saveBody(personId, planAId), adminToken).get("code").asInt());
        assertEquals(1, enrolled(planAId));
    }

    @Test
    @DisplayName("ADMIN 可维护已删除人员的历史记录，BUSINESS 不再访问")
    void adminCanMaintainHistoricalPersonRecords() throws Exception {
        disabledPersonMapper.deleteById(personAId);
        assertEquals(0, pageData("planId=" + planAId, businessToken).get("total").asLong());
        assertEquals(403, request("GET", API + "/" + enrolledAId, null, businessToken).get("code").asInt());
        assertSuccess(request("GET", API + "/" + enrolledAId, null, adminToken));
        assertEquals(3, pageData("personId=" + personAId, adminToken).get("total").asLong());
        assertSuccess(request("DELETE", API + "/" + enrolledAId, null, adminToken));
        assertEquals(1, enrolled(planAId));
    }

    private Long createUser(String username, String roleCode) {
        LambdaQueryWrapper<SysRolePO> query = new LambdaQueryWrapper<>();
        query.eq(SysRolePO::getRoleCode, roleCode);
        SysRolePO role = sysRoleMapper.selectOne(query);
        assertNotNull(role);
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

    private Long createGrid(String name, Long userId) {
        SysGridPO grid = new SysGridPO();
        grid.setGridName(name);
        grid.setGridWorkerId(userId);
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

    private Long createPlan(int capacity) {
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setPlanName("培训记录测试计划");
        plan.setStartDate(LocalDate.now().plusDays(10));
        plan.setEndDate(LocalDate.now().plusDays(14));
        plan.setCapacity(capacity);
        plan.setEnrolledCount(0);
        plan.setStatus(TrainingPlanService.STATUS_ENROLLING);
        plan.setPublishStatus(TrainingPlanService.PUBLISH_PUBLISHED);
        plan.setCreateBy(adminUserId);
        plan.setDeleted(0);
        trainingPlanMapper.insert(plan);
        return plan.getId();
    }

    private Long seedRecord(Long personId, Long planId, String status) {
        TrainingRecordPO record = new TrainingRecordPO();
        record.setPersonId(personId);
        record.setPlanId(planId);
        record.setStatus(status);
        record.setEnrollDate(LocalDate.of(2026, 10, 13));
        record.setRemark("记录测试备注");
        record.setDeleted(0);
        trainingRecordMapper.insert(record);
        if (!TrainingRecordService.STATUS_CANCELLED.equals(status)) {
            jdbcTemplate.update("UPDATE training_plan SET enrolled_count = enrolled_count + 1 WHERE id = ?", planId);
        }
        return record.getId();
    }

    private Map<String, Object> saveBody(Long personId, Long planId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personId", personId);
        body.put("planId", planId);
        return body;
    }

    private int enrolled(Long planId) {
        return jdbcTemplate.queryForObject("SELECT enrolled_count FROM training_plan WHERE id = ?", Integer.class, planId);
    }

    private long recordCount(Long planId) {
        LambdaQueryWrapper<TrainingRecordPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingRecordPO::getPlanId, planId);
        return trainingRecordMapper.selectCount(wrapper);
    }

    private String login(String username) throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername(username);
        dto.setPassword(PWD);
        JsonNode response = request("POST", "/api/auth/login", dto, null);
        assertSuccess(response);
        return response.get("data").get("token").asText();
    }

    private JsonNode pageData(String query, String token) throws Exception {
        JsonNode response = request("GET", API + "/page?" + query, null, token);
        assertSuccess(response);
        return response.get("data");
    }

    private List<Long> idsOf(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode record : page.get("records")) {
            ids.add(record.get("id").asLong());
        }
        return ids;
    }

    private void assertSuccess(JsonNode response) {
        assertEquals(200, response.get("code").asInt(), response.toString());
    }

    private List<JsonNode> parallelRequests(List<Callable<JsonNode>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (Callable<JsonNode> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return request.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<JsonNode> responses = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                responses.add(future.get(15, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
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
            return objectMapper.readTree(new String(
                    (status >= 400 ? connection.getErrorStream() : connection.getInputStream()).readAllBytes(),
                    StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}

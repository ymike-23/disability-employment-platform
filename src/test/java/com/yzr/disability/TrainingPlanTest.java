package com.yzr.disability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.service.TrainingPlanService;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 培训计划接口、发布流程及权限集成测试，仅使用 H2 测试库。
 *
 * <p>计划由管理员统一维护并发布；业务人员可查看全平台已发布计划，
 * 不能新增、修改、删除或发布计划。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingPlanTest {

    private static final String API = "/api/training-plan";
    private static final String PWD = "Test@123456";
    private static final String JOB_TYPE = "11";
    private static final String REHAB_TYPE = "1";
    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = START.plusDays(4);

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrainingPlanMapper trainingPlanMapper;
    @Autowired
    private SysDictMapper sysDictMapper;
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
    private Long adminUserId;
    private Long businessUserId;
    private Long businessBUserId;
    private Long gridAId;
    private Long gridBId;
    private String adminToken;
    private String businessToken;
    private String businessBToken;
    private String noGridToken;
    private String multiGridToken;
    private String workerToken;
    private int caseNumber;
    private String planPrefix;
    private Long draftAId;
    private Long enrollingAId;
    private Long endedAId;
    private Long cancelledAId;
    private Long publishedBId;

    @BeforeAll
    void setupUsers() throws Exception {
        adminUserId = createUser("tp_test_admin", "ADMIN");
        businessUserId = createUser("tp_test_business_a", "BUSINESS");
        businessBUserId = createUser("tp_test_business_b", "BUSINESS");
        createUser("tp_test_no_grid", "BUSINESS");
        Long multiGridUserId = createUser("tp_test_multi_grid", "BUSINESS");
        Long workerUserId = createUser("tp_test_worker", "GRID_WORKER");
        gridAId = createGrid("培训计划测试网格A", businessUserId);
        gridBId = createGrid("培训计划测试网格B", businessBUserId);
        createGrid("培训计划测试多网格1", multiGridUserId);
        createGrid("培训计划测试多网格2", multiGridUserId);
        createGrid("培训计划测试非业务角色网格", workerUserId);
        adminToken = login("tp_test_admin");
        businessToken = login("tp_test_business_a");
        businessBToken = login("tp_test_business_b");
        noGridToken = login("tp_test_no_grid");
        multiGridToken = login("tp_test_multi_grid");
        workerToken = login("tp_test_worker");
    }

    @BeforeEach
    void setupPlans() {
        // 独立名称范围隔离每个用例，分页和更新不依赖执行顺序。
        planPrefix = "培训计划测试" + (++caseNumber) + "-";
        draftAId = seedPlan("草稿计划", TrainingPlanService.PUBLISH_DRAFT, "2", JOB_TYPE,
                TrainingPlanService.STATUS_NOT_STARTED, 0);
        enrollingAId = seedPlan("报名中计划", TrainingPlanService.PUBLISH_PUBLISHED, "1", REHAB_TYPE,
                TrainingPlanService.STATUS_ENROLLING, 3);
        endedAId = seedPlan("已结束计划", TrainingPlanService.PUBLISH_PUBLISHED, "2", JOB_TYPE,
                TrainingPlanService.STATUS_ENDED, 0);
        cancelledAId = seedPlan("已取消计划", TrainingPlanService.PUBLISH_PUBLISHED, "1", REHAB_TYPE,
                TrainingPlanService.STATUS_CANCELLED, 0);
        publishedBId = seedPlan("其他网格计划", TrainingPlanService.PUBLISH_PUBLISHED, "2", JOB_TYPE,
                TrainingPlanService.STATUS_NOT_STARTED, 2);
    }

    @AfterAll
    void cleanupTokens() {
        for (Long userId : createdUserIds) {
            stringRedisTemplate.delete("login:token:" + userId);
        }
    }

    @Test
    @DisplayName("ADMIN 新增培训计划默认为草稿，报名人数固定为0")
    void createPlanCreatesDraft() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("planName", "  " + planPrefix + "新增计划  ");
        body.put("category", "2");
        body.put("trainingType", JOB_TYPE);
        body.put("location", "社区培训室");
        body.put("organizer", "残疾人服务中心");
        body.put("remark", "周末培训");
        JsonNode response = request("POST", API, body, adminToken);
        assertSuccess(response);
        TrainingPlanPO po = trainingPlanMapper.selectById(response.get("data").asLong());
        assertNotNull(po);
        assertEquals(planPrefix + "新增计划", po.getPlanName());
        assertEquals("2", po.getCategory());
        assertEquals(JOB_TYPE, po.getTrainingType());
        assertEquals(START, po.getStartDate());
        assertEquals(END, po.getEndDate());
        assertEquals(20, po.getCapacity());
        assertEquals(0, po.getEnrolledCount());
        assertEquals(TrainingPlanService.STATUS_NOT_STARTED, po.getStatus());
        assertEquals(TrainingPlanService.PUBLISH_DRAFT, po.getPublishStatus());
        assertEquals(0, po.getDeleted());
        assertEquals(adminUserId, po.getCreateBy());
        assertNotNull(po.getCreateTime());
        assertNotNull(po.getUpdateTime());
    }

    @Test
    @DisplayName("ADMIN 可发布草稿计划，重复发布和已取消计划均拒绝")
    void adminCanPublishPlan() throws Exception {
        assertEquals(403, request("PUT", API + "/" + draftAId + "/publish", null, businessToken)
                .get("code").asInt());
        assertEquals(TrainingPlanService.PUBLISH_DRAFT, trainingPlanMapper.selectById(draftAId).getPublishStatus());

        assertSuccess(request("PUT", API + "/" + draftAId + "/publish", null, adminToken));
        TrainingPlanPO published = trainingPlanMapper.selectById(draftAId);
        assertEquals(TrainingPlanService.PUBLISH_PUBLISHED, published.getPublishStatus());
        assertEquals(adminUserId, published.getUpdateBy());

        // 重复发布拒绝；普通修改接口不能篡改发布状态。
        assertEquals(409, request("PUT", API + "/" + draftAId + "/publish", null, adminToken)
                .get("code").asInt());
        Map<String, Object> body = saveBody();
        body.put("publishStatus", TrainingPlanService.PUBLISH_DRAFT);
        assertSuccess(request("PUT", API + "/" + draftAId, body, adminToken));
        assertEquals(TrainingPlanService.PUBLISH_PUBLISHED, trainingPlanMapper.selectById(draftAId).getPublishStatus());

        // 已取消的计划不能发布。
        assertEquals(409, request("PUT", API + "/" + cancelledAId + "/publish", null, adminToken)
                .get("code").asInt());
    }

    @Test
    @DisplayName("BUSINESS 不能新增、修改、删除或发布培训计划")
    void businessCannotWritePlans() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        assertEquals(403, request("POST", API, saveBody(), businessToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + enrollingAId, saveBody(), businessToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + enrollingAId, null, businessToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + draftAId + "/publish", null, businessBToken)
                .get("code").asInt());
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        TrainingPlanPO unchanged = trainingPlanMapper.selectById(enrollingAId);
        assertEquals("计划测试备注", unchanged.getRemark());
        assertEquals(TrainingPlanService.PUBLISH_DRAFT, trainingPlanMapper.selectById(draftAId).getPublishStatus());
    }

    @Test
    @DisplayName("不同网格的 BUSINESS 均可查看全平台已发布计划，草稿不可见")
    void businessSeesAllPublishedPlansRegardlessOfGrid() throws Exception {
        // 管理员可见全部 5 条，包含草稿。
        assertEquals(5, pageData(query(""), adminToken).get("total").asLong());
        // 两个不同网格的业务人员结果完全一致，且不依赖 create_by 或 grid_id。
        for (String token : List.of(businessToken, businessBToken)) {
            JsonNode page = pageData(query(""), token);
            assertEquals(4, page.get("total").asLong());
            List<Long> ids = idsOf(page);
            assertTrue(ids.contains(enrollingAId));
            assertTrue(ids.contains(endedAId));
            assertTrue(ids.contains(cancelledAId));
            assertTrue(ids.contains(publishedBId));
            assertFalse(ids.contains(draftAId));
        }
        assertEquals(403, request("GET", API + "/" + draftAId, null, businessToken).get("code").asInt());
        assertEquals(403, request("GET", API + "/" + Long.MAX_VALUE, null, businessToken).get("code").asInt());
        assertSuccess(request("GET", API + "/" + draftAId, null, adminToken));
    }

    @Test
    @DisplayName("BUSINESS 分页忽略草稿筛选条件，ADMIN 可按发布状态筛选")
    void publishStatusFilterIsScopedByRole() throws Exception {
        assertEquals(1, pageData(query("&publishStatus=0"), adminToken).get("total").asLong());
        assertEquals(List.of(draftAId), idsOf(pageData(query("&publishStatus=0"), adminToken)));
        assertEquals(4, pageData(query("&publishStatus=1"), adminToken).get("total").asLong());
        // 业务人员传入草稿筛选也只返回已发布计划。
        assertEquals(4, pageData(query("&publishStatus=0"), businessToken).get("total").asLong());
        for (String invalid : List.of("2", "已发布")) {
            assertEquals(400, request("GET", API + "/page?publishStatus=" + enc(invalid), null, adminToken)
                    .get("code").asInt());
        }
    }

    @Test
    @DisplayName("分页返回正确总数、页数及不同页记录")
    void pagePlans() throws Exception {
        JsonNode first = pageData(query("&current=1&size=1"), businessToken);
        JsonNode second = pageData(query("&current=2&size=1"), businessToken);
        assertEquals(4, first.get("total").asLong());
        assertEquals(4, first.get("pages").asLong());
        assertEquals(1, first.get("size").asLong());
        assertEquals(1, first.get("records").size());
        assertEquals(2, second.get("current").asLong());
        assertNotEquals(first.get("records").get(0).get("id").asLong(),
                second.get("records").get(0).get("id").asLong());
    }

    @Test
    @DisplayName("计划名称、大类、培训类型及状态筛选同时生效")
    void pageFiltersPlan() throws Exception {
        JsonNode page = pageData(query("&category=2&trainingType=" + enc(JOB_TYPE) + "&status=1"), businessToken);
        assertEquals(1, page.get("total").asLong());
        assertEquals(List.of(publishedBId), idsOf(page));
        JsonNode named = pageData("planName=" + enc(planPrefix + "报名中"), adminToken);
        assertEquals(List.of(enrollingAId), idsOf(named));
        assertEquals(List.of(enrollingAId), idsOf(pageData(query("&category=1&status=1"), businessToken)));
    }

    @Test
    @DisplayName("详情返回计划字段，不暴露逻辑删除和审计账号字段")
    void detailPlan() throws Exception {
        JsonNode response = request("GET", API + "/" + enrollingAId, null, businessToken);
        assertSuccess(response);
        JsonNode data = response.get("data");
        assertEquals(enrollingAId.longValue(), data.get("id").asLong());
        assertEquals(START.toString(), data.get("startDate").asText());
        assertEquals(END.toString(), data.get("endDate").asText());
        assertEquals(20, data.get("capacity").asInt());
        assertEquals(3, data.get("enrolledCount").asInt());
        assertEquals(TrainingPlanService.PUBLISH_PUBLISHED, data.get("publishStatus").asText());
        assertFalse(data.has("deleted"));
        assertFalse(data.has("createBy"));
    }

    @Test
    @DisplayName("修改成功，未传可选字段保留，并记录更新人")
    void updatePlan() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("planName", planPrefix + "更新后的计划");
        body.put("capacity", 25);
        body.put("status", TrainingPlanService.STATUS_IN_PROGRESS);
        body.put("location", "新的培训地点");
        assertSuccess(request("PUT", API + "/" + enrollingAId, body, adminToken));
        TrainingPlanPO po = trainingPlanMapper.selectById(enrollingAId);
        assertEquals(planPrefix + "更新后的计划", po.getPlanName());
        assertEquals(25, po.getCapacity());
        assertEquals(3, po.getEnrolledCount());
        assertEquals("新的培训地点", po.getLocation());
        assertEquals(TrainingPlanService.STATUS_IN_PROGRESS, po.getStatus());
        assertEquals(REHAB_TYPE, po.getTrainingType());
        assertEquals("计划测试主办单位", po.getOrganizer());
        assertEquals(adminUserId, po.getUpdateBy());
    }

    @Test
    @DisplayName("报名人数只能由培训记录维护，计划接口不能写入或篡改")
    void enrolledCountCannotBeModified() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("enrolledCount", 50);
        JsonNode create = request("POST", API, body, adminToken);
        assertSuccess(create);
        assertEquals(0, trainingPlanMapper.selectById(create.get("data").asLong()).getEnrolledCount());

        Map<String, Object> update = saveBody();
        update.put("enrolledCount", 0);
        assertSuccess(request("PUT", API + "/" + enrollingAId, update, adminToken));
        assertEquals(3, trainingPlanMapper.selectById(enrollingAId).getEnrolledCount());
    }

    @Test
    @DisplayName("删除为逻辑删除，分页和详情排除已删除计划")
    void deletePlanLogically() throws Exception {
        assertSuccess(request("DELETE", API + "/" + enrollingAId, null, adminToken));
        assertNull(trainingPlanMapper.selectById(enrollingAId));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT deleted FROM training_plan WHERE id = ?", Integer.class, enrollingAId));
        JsonNode page = pageData(query(""), businessToken);
        assertEquals(3, page.get("total").asLong());
        assertFalse(idsOf(page).contains(enrollingAId));
        assertEquals(404, request("GET", API + "/" + enrollingAId, null, adminToken).get("code").asInt());
        assertEquals(404, request("DELETE", API + "/" + enrollingAId, null, adminToken).get("code").asInt());
    }

    @Test
    @DisplayName("ADMIN 可以管理全部计划，包含草稿和历史计划")
    void adminCanManageAllPlans() throws Exception {
        assertEquals(5, pageData(query(""), adminToken).get("total").asLong());
        assertSuccess(request("GET", API + "/" + publishedBId, null, adminToken));
        assertSuccess(request("GET", API + "/" + draftAId, null, adminToken));
        JsonNode create = request("POST", API, saveBody(), adminToken);
        assertSuccess(create);
        assertEquals(adminUserId, trainingPlanMapper.selectById(create.get("data").asLong()).getCreateBy());
        Map<String, Object> body = saveBody();
        body.put("remark", "管理员修改其他网格计划");
        assertSuccess(request("PUT", API + "/" + publishedBId, body, adminToken));
        assertEquals(adminUserId, trainingPlanMapper.selectById(publishedBId).getUpdateBy());
        assertSuccess(request("DELETE", API + "/" + publishedBId, null, adminToken));
        assertNull(trainingPlanMapper.selectById(publishedBId));
    }

    @Test
    @DisplayName("不存在的计划不能查询、修改、删除或发布，BUSINESS 不泄露存在性")
    void missingPlanIsRejected() throws Exception {
        String path = API + "/" + Long.MAX_VALUE;
        assertEquals(404, request("GET", path, null, adminToken).get("code").asInt());
        assertEquals(404, request("PUT", path, saveBody(), adminToken).get("code").asInt());
        assertEquals(404, request("DELETE", path, null, adminToken).get("code").asInt());
        assertEquals(404, request("PUT", path + "/publish", null, adminToken).get("code").asInt());
        assertEquals(403, request("GET", path, null, businessToken).get("code").asInt());
        JsonNode missing = request("GET", path, null, businessToken);
        JsonNode forbidden = request("GET", API + "/" + draftAId, null, businessToken);
        assertEquals(403, forbidden.get("code").asInt());
        assertEquals(missing.get("message").asText(), forbidden.get("message").asText());
    }

    @Test
    @DisplayName("日期倒置禁止新增和修改，开始日期等于结束日期允许")
    void datesAreValidated() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        Map<String, Object> body = saveBody();
        body.put("startDate", END.toString());
        body.put("endDate", START.toString());
        assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        assertEquals(400, request("PUT", API + "/" + draftAId, body, adminToken).get("code").asInt());
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        assertEquals(START, trainingPlanMapper.selectById(draftAId).getStartDate());
        assertEquals(END, trainingPlanMapper.selectById(draftAId).getEndDate());
        body.put("startDate", START.toString());
        JsonNode create = request("POST", API, body, adminToken);
        assertSuccess(create);
        TrainingPlanPO po = trainingPlanMapper.selectById(create.get("data").asLong());
        assertEquals(po.getStartDate(), po.getEndDate());
    }

    @Test
    @DisplayName("容量为0或负数时禁止新增和修改")
    void capacityMustBePositive() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        for (int capacity : List.of(0, -1)) {
            Map<String, Object> body = saveBody();
            body.put("capacity", capacity);
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
            assertEquals(400, request("PUT", API + "/" + draftAId, body, adminToken).get("code").asInt());
        }
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        assertEquals(20, trainingPlanMapper.selectById(draftAId).getCapacity());
    }

    @Test
    @DisplayName("新容量不能小于当前已报名人数，且不能通过篡改报名人数绕过")
    void capacityCannotDropBelowCurrentEnrollments() throws Exception {
        Map<String, Object> body = saveBody();
        body.put("capacity", 2);
        assertEquals(400, request("PUT", API + "/" + enrollingAId, body, adminToken).get("code").asInt());
        body.put("enrolledCount", 0);
        assertEquals(400, request("PUT", API + "/" + enrollingAId, body, adminToken).get("code").asInt());
        TrainingPlanPO unchanged = trainingPlanMapper.selectById(enrollingAId);
        assertEquals(20, unchanged.getCapacity());
        assertEquals(3, unchanged.getEnrolledCount());
        body.remove("enrolledCount");
        body.put("capacity", 3);
        assertSuccess(request("PUT", API + "/" + enrollingAId, body, adminToken));
        assertEquals(3, trainingPlanMapper.selectById(enrollingAId).getCapacity());
        assertEquals(3, trainingPlanMapper.selectById(enrollingAId).getEnrolledCount());
    }

    @Test
    @DisplayName("状态接受 training_status 字典已有的0、1、2、3、4")
    void statusUsesExistingDefinition() throws Exception {
        Map<String, Object> body = saveBody();
        for (String status : List.of("0", "1", "2", "3", "4")) {
            body.put("status", status);
            assertSuccess(request("PUT", API + "/" + draftAId, body, adminToken));
            assertEquals(status, trainingPlanMapper.selectById(draftAId).getStatus());
        }
        body.put("status", "999");
        assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        assertEquals(400, request("PUT", API + "/" + draftAId, body, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?status=999", null, adminToken).get("code").asInt());
        assertEquals("4", trainingPlanMapper.selectById(draftAId).getStatus());
    }

    @Test
    @DisplayName("培训计划只保存字典值，拒绝中文标签和不存在的字典值")
    void dictionaryValuesAreRequired() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        Map<String, String> dictFields = Map.of(
                "category", "training_category",
                "trainingType", "training_type",
                "status", "training_status");
        for (Map.Entry<String, String> field : dictFields.entrySet()) {
            String value = field.getKey().equals("status") ? "2" : "1";
            SysDictPO dict = dictOf(field.getValue(), value);
            Map<String, Object> body = saveBody();
            body.put(field.getKey(), dict.getDictLabel());
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
            assertEquals(400, request("PUT", API + "/" + draftAId, body, adminToken).get("code").asInt());
            assertEquals(400, request("GET", API + "/page?" + field.getKey() + "=" + enc(dict.getDictLabel()),
                    null, adminToken).get("code").asInt());
            body.put(field.getKey(), "999");
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        }
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
        JsonNode detail = request("GET", API + "/" + enrollingAId, null, adminToken).get("data");
        assertEquals("1", detail.get("category").asText());
        assertEquals(REHAB_TYPE, detail.get("trainingType").asText());
        assertEquals(TrainingPlanService.STATUS_ENROLLING, detail.get("status").asText());
    }

    @Test
    @DisplayName("停用字典项后，新增、修改和筛选均拒绝该值")
    void disabledDictionaryValuesAreRejected() throws Exception {
        Map<String, String> dictFields = Map.of(
                "category", "training_category",
                "trainingType", "training_type",
                "status", "training_status",
                "publishStatus", "training_publish_status");
        for (Map.Entry<String, String> field : dictFields.entrySet()) {
            SysDictPO dict = dictOf(field.getValue(), "1");
            Integer originalStatus = dict.getStatus();
            try {
                dict.setStatus(0);
                sysDictMapper.updateById(dict);
                Map<String, Object> body = saveBody();
                body.put(field.getKey(), "1");
                assertEquals(400, request("GET", API + "/page?" + field.getKey() + "=1",
                        null, adminToken).get("code").asInt());
                if (!"publishStatus".equals(field.getKey())) {
                    assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
                }
            } finally {
                dict.setStatus(originalStatus);
                sysDictMapper.updateById(dict);
            }
        }
        // 默认状态与默认发布状态同样必须是启用字典项。
        SysDictPO defaultStatus = dictOf("training_status", TrainingPlanService.STATUS_NOT_STARTED);
        Integer originalStatus = defaultStatus.getStatus();
        try {
            defaultStatus.setStatus(0);
            sysDictMapper.updateById(defaultStatus);
            assertEquals(400, request("POST", API, saveBody(), adminToken).get("code").asInt());
        } finally {
            defaultStatus.setStatus(originalStatus);
            sysDictMapper.updateById(defaultStatus);
        }
        SysDictPO defaultPublish = dictOf("training_publish_status", TrainingPlanService.PUBLISH_PUBLISHED);
        Integer originalPublishStatus = defaultPublish.getStatus();
        try {
            defaultPublish.setStatus(0);
            sysDictMapper.updateById(defaultPublish);
            assertEquals(400, request("PUT", API + "/" + draftAId + "/publish", null, adminToken)
                    .get("code").asInt());
        } finally {
            defaultPublish.setStatus(originalPublishStatus);
            sysDictMapper.updateById(defaultPublish);
        }
    }

    @Test
    @DisplayName("名称、日期及容量必填，文本长度及查询分页参数校验生效")
    void invalidInputIsRejected() throws Exception {
        long before = trainingPlanMapper.selectCount(null);
        for (String field : List.of("planName", "startDate", "endDate", "capacity")) {
            Map<String, Object> body = saveBody();
            body.remove(field);
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        }
        for (String name : List.of("", "   ", "长".repeat(101))) {
            Map<String, Object> body = saveBody();
            body.put("planName", name);
            assertEquals(400, request("POST", API, body, adminToken).get("code").asInt());
        }
        Map<String, Object> invalidCategory = saveBody();
        invalidCategory.put("category", "3");
        assertEquals(400, request("POST", API, invalidCategory, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?current=0", null, adminToken).get("code").asInt());
        assertEquals(400, request("GET", API + "/page?size=101", null, adminToken).get("code").asInt());
        assertEquals(before, trainingPlanMapper.selectCount(null).longValue());
    }

    @Test
    @DisplayName("无网格或多网格账号仍可查看已发布计划，但不能维护计划")
    void gridStateDoesNotBlockPublishedPlanViewing() throws Exception {
        for (String token : List.of(noGridToken, multiGridToken)) {
            assertEquals(4, pageData(query(""), token).get("total").asLong());
            assertEquals(403, request("POST", API, saveBody(), token).get("code").asInt());
            assertEquals(403, request("PUT", API + "/" + draftAId, saveBody(), token).get("code").asInt());
            assertEquals(403, request("DELETE", API + "/" + draftAId, null, token).get("code").asInt());
            assertEquals(403, request("PUT", API + "/" + draftAId + "/publish", null, token).get("code").asInt());
        }
        assertNotNull(trainingPlanMapper.selectById(draftAId));
    }

    @Test
    @DisplayName("未登录或无ADMIN/BUSINESS角色时拒绝访问")
    void authenticationAndRolesAreRequired() throws Exception {
        assertEquals(401, request("GET", API + "/page", null, null).get("code").asInt());
        assertEquals(403, request("GET", API + "/page", null, workerToken).get("code").asInt());
        assertEquals(403, request("GET", API + "/" + draftAId, null, workerToken).get("code").asInt());
        assertEquals(403, request("POST", API, saveBody(), workerToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + draftAId, saveBody(), workerToken).get("code").asInt());
        assertEquals(403, request("DELETE", API + "/" + draftAId, null, workerToken).get("code").asInt());
        assertEquals(403, request("PUT", API + "/" + draftAId + "/publish", null, workerToken).get("code").asInt());
    }

    private SysDictPO dictOf(String dictType, String dictValue) {
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, dictType).eq(SysDictPO::getDictValue, dictValue);
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        assertNotNull(dict);
        return dict;
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

    private Long seedPlan(String name, String publishStatus, String category, String trainingType,
                          String status, int enrolledCount) {
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setPlanName(planPrefix + name);
        plan.setCategory(category);
        plan.setTrainingType(trainingType);
        plan.setStartDate(START);
        plan.setEndDate(END);
        if (TrainingPlanService.STATUS_ENDED.equals(status)) {
            plan.setStartDate(LocalDate.now().minusDays(10));
            plan.setEndDate(LocalDate.now().minusDays(1));
        }
        plan.setCapacity(20);
        plan.setEnrolledCount(enrolledCount);
        plan.setOrganizer("计划测试主办单位");
        plan.setStatus(status);
        plan.setPublishStatus(publishStatus);
        plan.setRemark("计划测试备注");
        plan.setDeleted(0);
        plan.setCreateBy(adminUserId);
        trainingPlanMapper.insert(plan);
        return plan.getId();
    }

    private Map<String, Object> saveBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planName", planPrefix + "新增或更新计划");
        body.put("startDate", START.toString());
        body.put("endDate", END.toString());
        body.put("capacity", 20);
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

    private String query(String extra) {
        return "planName=" + enc(planPrefix) + extra;
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

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

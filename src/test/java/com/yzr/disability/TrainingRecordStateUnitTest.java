package com.yzr.disability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.util.TrainingRecordState;
import com.yzr.disability.entity.dto.TrainingRecordQueryDTO;
import com.yzr.disability.entity.po.*;
import com.yzr.disability.mapper.*;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.impl.TrainingRecordServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不启动应用，仅使用Mockito及H2内存中的SELECT，不访问业务库或Redis。 */
class TrainingRecordStateUnitTest {
    private final LocalDate end = LocalDate.of(2026, 10, 30);

    private TrainingPlanPO plan() {
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setId(20L); plan.setDeleted(0); plan.setPublishStatus("1"); plan.setStatus("1");
        plan.setStartDate(end.minusDays(2)); plan.setEndDate(end);
        return plan;
    }

    @Test
    void onlyUnfinishedParticipantsAdvanceAfterTheEndDate() {
        TrainingPlanPO plan = plan();
        for (String status : List.of("1", "2")) {
            assertEquals(status, TrainingRecordState.effectiveStatus(status, plan, end.minusDays(1)));
            assertEquals(status, TrainingRecordState.effectiveStatus(status, plan, end));
            assertEquals("3", TrainingRecordState.effectiveStatus(status, plan, end.plusDays(1)));
        }
        for (String status : new String[]{null, "3", "4", "5", "other"})
            assertEquals(status, TrainingRecordState.effectiveStatus(status, plan, end.plusDays(1)));
        assertEquals("1", plan.getStatus());
    }

    @Test
    void sqlFilteringMatchesTheReturnedStatusForDatesAndManagementStates() throws Exception {
        String sql = """
                WITH training_plan AS (
                    SELECT 20 AS id, CAST(? AS INT) AS deleted, CAST(? AS VARCHAR) AS publish_status,
                           CAST(? AS VARCHAR) AS status, CAST(? AS DATE) AS start_date, CAST(? AS DATE) AS end_date
                ), training_record AS (SELECT 20 AS plan_id, CAST(? AS VARCHAR) AS status)
                SELECT
                """ + TrainingRecordState.STATUS_SQL.replace("{0}", "?") + " AS effective_status FROM training_record";
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:training_record_state;MODE=MySQL");
             var statement = connection.prepareStatement(sql)) {
            for (String planStatus : new String[]{null, "0", "1", "2", "3", "4", "other"})
                for (String publish : List.of("0", "1"))
                    for (int deleted : new int[]{0, 1})
                        for (String status : new String[]{null, "1", "2", "3", "4", "5"})
                            for (LocalDate start : new LocalDate[]{end.minusDays(2), end.plusDays(1), null})
                                for (LocalDate today : List.of(end.minusDays(1), end, end.plusDays(1))) {
                                    TrainingPlanPO plan = plan(); plan.setStatus(planStatus);
                                    plan.setPublishStatus(publish); plan.setDeleted(deleted); plan.setStartDate(start);
                                    statement.setObject(1, deleted); statement.setObject(2, publish);
                                    statement.setObject(3, planStatus); statement.setObject(4, start);
                                    statement.setObject(5, end); statement.setObject(6, status); statement.setObject(7, today);
                                    try (var rows = statement.executeQuery()) {
                                        assertTrue(rows.next());
                                        assertEquals(TrainingRecordState.effectiveStatus(status, plan, today), rows.getString(1));
                                    }
                                }
        }
        assertEquals("1", TrainingRecordState.effectiveStatus("1", null, end.plusDays(1)));
        TrainingPlanPO missingDate = plan(); missingDate.setEndDate(null);
        assertEquals("1", TrainingRecordState.effectiveStatus("1", missingDate, end.plusDays(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAndDetailUseTheSameCompletionRuleWithoutWritingOrChangingOutcomes() {
        for (Class<?> type : new Class<?>[]{TrainingRecordPO.class, TrainingPlanPO.class, SysDictPO.class})
            if (TableInfoHelper.getTableInfo(type) == null)
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        TrainingRecordMapper records = mock(TrainingRecordMapper.class);
        TrainingPlanMapper plans = mock(TrainingPlanMapper.class);
        SysDictMapper dictionaries = mock(SysDictMapper.class);
        GridScopeService grids = mock(GridScopeService.class);
        TrainingRecordServiceImpl service = new TrainingRecordServiceImpl();
        ReflectionTestUtils.setField(service, "trainingRecordMapper", records);
        ReflectionTestUtils.setField(service, "trainingPlanMapper", plans);
        ReflectionTestUtils.setField(service, "sysDictMapper", dictionaries);
        ReflectionTestUtils.setField(service, "gridScopeService", grids);
        when(grids.isAdmin()).thenReturn(true);
        SysDictPO dict = new SysDictPO(); dict.setDictValue("3");
        when(dictionaries.selectOne(any())).thenReturn(dict);
        TrainingRecordPO record = new TrainingRecordPO(); record.setId(1L); record.setPersonId(10L);
        record.setPlanId(20L); record.setStatus("1"); record.setResult("0");
        TrainingPlanPO plan = plan(); plan.setStartDate(LocalDate.now().minusDays(2));
        plan.setEndDate(LocalDate.now().minusDays(1));
        when(plans.selectList(any())).thenReturn(List.of(plan));
        when(plans.selectById(20L)).thenReturn(plan);
        when(records.selectById(1L)).thenReturn(record);
        when(records.selectPage(any(Page.class), any())).thenAnswer(call -> {
            LambdaQueryWrapper<TrainingRecordPO> wrapper = call.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("CASE WHEN training_record.status"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(LocalDate.now()));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("3"));
            Page<TrainingRecordPO> page = call.getArgument(0);
            page.setRecords(List.of(record)); page.setTotal(1); return page;
        });
        TrainingRecordQueryDTO query = new TrainingRecordQueryDTO(); query.setStatus("3");
        var page = service.page(query);
        assertEquals(1, page.getTotal());
        assertEquals("3", page.getRecords().getFirst().getStatus());
        var detail = service.detail(1L);
        assertEquals("3", detail.getStatus()); assertEquals("0", detail.getResult());
        assertNull(detail.getCertificateNo()); assertNull(detail.getAcquiredSkills());
        assertEquals("1", record.getStatus());
        verify(records, never()).update(nullable(TrainingRecordPO.class), any());
        verify(records, never()).incrementEnrollment(anyLong(), any());
        verify(records, never()).decrementEnrollment(anyLong(), any());
        when(grids.isAdmin()).thenReturn(false);
        when(grids.requireSingleGridId()).thenThrow(new BusinessException(403, "无网格权限"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.detail(1L)).getCode());
    }
}

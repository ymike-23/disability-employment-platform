package com.yzr.disability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.util.TrainingPlanState;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.impl.TrainingPlanServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingPlanStateUnitTest {
    private final LocalDate start = LocalDate.of(2026, 10, 10);
    private TrainingPlanPO plan() {
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setId(20L); plan.setPublishStatus("1"); plan.setStatus("0");
        plan.setStartDate(start); plan.setEndDate(start.plusDays(2));
        return plan;
    }

    @Test
    void publishedPlanFollowsInclusiveDateBoundariesWithoutChangingStoredState() {
        TrainingPlanPO plan = plan();
        assertEquals("1", TrainingPlanState.effectiveStatus(plan, start.minusDays(1)));
        assertEquals("2", TrainingPlanState.effectiveStatus(plan, start));
        assertEquals("2", TrainingPlanState.effectiveStatus(plan, start.plusDays(2)));
        assertEquals("3", TrainingPlanState.effectiveStatus(plan, start.plusDays(3)));
        assertEquals("0", plan.getStatus());
    }

    @Test
    void draftsCancellationsUnknownStatesAndInvalidDatesArePreserved() {
        TrainingPlanPO plan = plan(); plan.setPublishStatus("0");
        assertEquals("0", TrainingPlanState.effectiveStatus(plan, start));
        plan.setPublishStatus("1"); plan.setStatus("4");
        assertEquals("4", TrainingPlanState.effectiveStatus(plan, start.plusDays(10)));
        plan.setStatus("other");
        assertEquals("other", TrainingPlanState.effectiveStatus(plan, start));
        plan.setStatus(null);
        assertNull(TrainingPlanState.effectiveStatus(plan, start));
        plan.setStatus("1"); plan.setEndDate(start.minusDays(1));
        assertFalse(TrainingPlanState.validDates(plan));
        assertEquals("1", TrainingPlanState.effectiveStatus(plan, start));
        plan.setEndDate(null);
        assertEquals("1", TrainingPlanState.effectiveStatus(plan, start));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByDynamicCaseBeforePaginationAndReturnsDynamicStatus() {
        for (Class<?> type : new Class<?>[]{TrainingPlanPO.class, SysDictPO.class})
            if (TableInfoHelper.getTableInfo(type) == null)
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        TrainingPlanMapper mapper = mock(TrainingPlanMapper.class);
        SysDictMapper dictionaries = mock(SysDictMapper.class);
        GridScopeService grids = mock(GridScopeService.class);
        when(grids.isAdmin()).thenReturn(true);
        SysDictPO value = new SysDictPO(); value.setDictValue("3");
        when(dictionaries.selectOne(any())).thenReturn(value);
        TrainingPlanPO plan = plan(); plan.setStartDate(LocalDate.now().minusDays(3));
        plan.setEndDate(LocalDate.now().minusDays(1));
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(a -> {
            LambdaQueryWrapper<TrainingPlanPO> query = a.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("CASE WHEN publish_status"));
            assertTrue(sql.contains("start_date <= end_date"));
            assertTrue(query.getParamNameValuePairs().containsValue(LocalDate.now()));
            assertTrue(query.getParamNameValuePairs().containsValue("3"));
            Page<TrainingPlanPO> page = a.getArgument(0);
            page.setRecords(List.of(plan)); page.setTotal(1); return page;
        });
        TrainingPlanServiceImpl service = new TrainingPlanServiceImpl();
        ReflectionTestUtils.setField(service, "trainingPlanMapper", mapper);
        ReflectionTestUtils.setField(service, "sysDictMapper", dictionaries);
        ReflectionTestUtils.setField(service, "gridScopeService", grids);
        TrainingPlanQueryDTO query = new TrainingPlanQueryDTO(); query.setStatus("3");
        assertEquals("3", service.page(query).getRecords().getFirst().getStatus());
        when(mapper.selectById(20L)).thenReturn(plan);
        assertEquals("3", service.detail(20L).getStatus());
        verify(mapper, never()).updateById(any(TrainingPlanPO.class));
    }
}


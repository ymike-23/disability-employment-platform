package com.yzr.disability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.entity.dto.TrainingRecordSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.po.TrainingRecordPO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.TrainingRecordMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import com.yzr.disability.service.TrainingRecordService;
import com.yzr.disability.service.impl.TrainingRecordServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingRecordDuplicateKeyTest {

    @Test
    @DisplayName("唯一键兜底冲突返回409，不能累加报名人数")
    void duplicateKeyIsReportedAsConflictWithoutIncrement() {
        // 独立运行时也初始化字典列元数据，不依赖 Spring 测试的执行顺序。
        if (TableInfoHelper.getTableInfo(SysDictPO.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDictPO.class);
        }
        TrainingRecordMapper recordMapper = mock(TrainingRecordMapper.class);
        DisabledPersonMapper personMapper = mock(DisabledPersonMapper.class);
        SysDictMapper dictMapper = mock(SysDictMapper.class);
        GridScopeService gridScopeService = mock(GridScopeService.class);
        TrainingRecordServiceImpl service = new TrainingRecordServiceImpl();
        ReflectionTestUtils.setField(service, "trainingRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "disabledPersonMapper", personMapper);
        ReflectionTestUtils.setField(service, "sysDictMapper", dictMapper);
        ReflectionTestUtils.setField(service, "gridScopeService", gridScopeService);

        when(gridScopeService.isAdmin()).thenReturn(true);
        when(personMapper.selectAccessibleForUpdate(1L, null)).thenReturn(new DisabledPersonPO());
        TrainingPlanPO plan = new TrainingPlanPO();
        plan.setId(2L);
        plan.setDeleted(0);
        plan.setCapacity(10);
        plan.setEnrolledCount(0);
        plan.setPublishStatus(TrainingPlanService.PUBLISH_PUBLISHED);
        plan.setStatus(TrainingPlanService.STATUS_ENROLLING);
        plan.setStartDate(java.time.LocalDate.now().plusDays(1));
        plan.setEndDate(java.time.LocalDate.now().plusDays(5));
        when(recordMapper.selectPlanForUpdate(2L)).thenReturn(plan);
        SysDictPO status = new SysDictPO();
        status.setDictValue(TrainingRecordService.STATUS_ENROLLED);
        when(dictMapper.selectOne(any())).thenReturn(status);
        when(recordMapper.countEnrollment(1L, 2L)).thenReturn(0L);
        when(recordMapper.insert(any(TrainingRecordPO.class)))
                .thenThrow(new DuplicateKeyException("uk_person_plan"));
        TrainingRecordSaveDTO dto = new TrainingRecordSaveDTO();
        dto.setPersonId(1L);
        dto.setPlanId(2L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(409, exception.getCode());
        assertEquals("该人员已报名该培训计划", exception.getMessage());
        verify(recordMapper).insert(any(TrainingRecordPO.class));
        verify(recordMapper, never()).incrementEnrollment(anyLong(), any());
    }
}

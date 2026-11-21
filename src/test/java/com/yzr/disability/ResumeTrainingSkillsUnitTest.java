package com.yzr.disability;

import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.entity.dto.ResumeSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.ResumePO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.ResumeMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResumeTrainingSkillsUnitTest {
    ResumeMapper mapper;
    ResumeServiceImpl service;
    ResumePO existing;

    @BeforeEach
    void prepare() {
        mapper = mock(ResumeMapper.class);
        DisabledPersonMapper people = mock(DisabledPersonMapper.class);
        GridScopeService grids = mock(GridScopeService.class);
        when(grids.isAdmin()).thenReturn(true);
        when(people.selectAccessibleForUpdate(10L, null)).thenReturn(new DisabledPersonPO());
        existing = new ResumePO(); existing.setId(30L); existing.setPersonId(10L);
        existing.setSkillsSummary("人工沟通技能；Excel"); existing.setJobIntention("文员");
        when(mapper.selectActiveIdsForUpdate(10L)).thenReturn(List.of(30L));
        when(mapper.selectForUpdate(30L, 10L)).thenReturn(existing);
        when(mapper.updateById(any(ResumePO.class))).thenReturn(1);
        when(mapper.insert(any(ResumePO.class))).thenAnswer(a -> { ((ResumePO) a.getArgument(0)).setId(31L); return 1; });
        when(mapper.selectConfirmedEmploymentSkills(10L, "1", "2")).thenReturn(List.of("Excel；文字录入"));
        service = new ResumeServiceImpl();
        ReflectionTestUtils.setField(service, "resumeMapper", mapper);
        ReflectionTestUtils.setField(service, "disabledPersonMapper", people);
        ReflectionTestUtils.setField(service, "gridScopeService", grids);
    }

    @Test
    void automaticMergePreservesManualSkillsAndOnlyUpdatesSkillFields() {
        service.mergeConfirmedTrainingSkills(10L, "excel；文字录入；文字录入");
        ArgumentCaptor<ResumePO> changes = ArgumentCaptor.forClass(ResumePO.class);
        verify(mapper).updateById(changes.capture());
        assertEquals("人工沟通技能；Excel；文字录入", changes.getValue().getSkillsSummary());
        assertNull(changes.getValue().getJobIntention()); assertNull(changes.getValue().getExpectedSalary());
        assertNull(changes.getValue().getSelfEvaluation()); assertNull(changes.getValue().getPersonId());
        verify(mapper, never()).insert(any(ResumePO.class));
    }

    @Test
    void repeatedSkillsDoNotUpdateOrDuplicateResume() {
        service.mergeConfirmedTrainingSkills(10L, "Excel；人工沟通技能");
        verify(mapper, never()).updateById(any(ResumePO.class)); verify(mapper, never()).insert(any(ResumePO.class));
    }

    @Test
    void missingResumeIncludesEarlierConfirmedSkillsWithoutInventedFields() {
        when(mapper.selectActiveIdsForUpdate(10L)).thenReturn(List.of());
        service.mergeConfirmedTrainingSkills(10L, "Excel");
        ArgumentCaptor<ResumePO> created = ArgumentCaptor.forClass(ResumePO.class);
        verify(mapper).insert(created.capture());
        assertEquals(10L, created.getValue().getPersonId());
        assertEquals("Excel；文字录入", created.getValue().getSkillsSummary());
        assertNull(created.getValue().getJobIntention()); assertNull(created.getValue().getExpectedSalary());
        assertNull(created.getValue().getSelfEvaluation());
    }

    @Test
    void multipleCurrentResumesAreRejectedRatherThanCreatingAnother() {
        when(mapper.selectActiveIdsForUpdate(10L)).thenReturn(List.of(30L, 31L));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> service.mergeConfirmedTrainingSkills(10L, "Excel")).getCode());
        verify(mapper, never()).insert(any(ResumePO.class));
    }

    @Test
    void createUsesConfirmedSkillsAndNeverInfersFromTrainingNames() {
        when(mapper.selectActiveIdsForUpdate(10L)).thenReturn(List.of());
        ResumeSaveDTO dto = new ResumeSaveDTO(); dto.setPersonId(10L); dto.setSkillsSummary("人工技能");
        service.create(dto);
        ArgumentCaptor<ResumePO> created = ArgumentCaptor.forClass(ResumePO.class);
        verify(mapper).insert(created.capture());
        assertEquals("人工技能；Excel；文字录入", created.getValue().getSkillsSummary());
        verify(mapper, never()).selectCompletedEmploymentTrainingNames(anyLong(), anyString(), anyString());
    }

    @Test
    void oversizedMergedSummaryFailsInsteadOfTruncatingManualSkills() {
        existing.setSkillsSummary("甲".repeat(22000));
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.mergeConfirmedTrainingSkills(10L, "Excel")).getCode());
        verify(mapper, never()).updateById(any(ResumePO.class));
    }
}


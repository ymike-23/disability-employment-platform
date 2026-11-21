package com.yzr.disability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.entity.dto.EmploymentAssessmentQueryDTO;
import com.yzr.disability.entity.dto.EmploymentAssessmentSaveDTO;
import com.yzr.disability.entity.dto.TrainingCertificateQueryDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.EmploymentAssessmentPO;
import com.yzr.disability.entity.po.TrainingCertificatePO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.EmploymentAssessmentMapper;
import com.yzr.disability.mapper.TrainingCertificateMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.impl.EmploymentAssessmentServiceImpl;
import com.yzr.disability.service.impl.TrainingCertificateServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 所有Mapper均为Mock；只检查业务分支和生成的权限查询条件。 */
class EmploymentAssessmentUnitTest {
    EmploymentAssessmentMapper assessments;
    TrainingCertificateMapper certificates;
    DisabledPersonMapper people;
    GridScopeService grids;
    EmploymentAssessmentServiceImpl service;
    TrainingCertificateServiceImpl certificateService;
    DisabledPersonPO person;
    EmploymentAssessmentPO existing;
    TrainingCertificatePO certificate;

    @BeforeEach
    void prepare() {
        for (Class<?> type : new Class<?>[]{EmploymentAssessmentPO.class, TrainingCertificatePO.class})
            if (TableInfoHelper.getTableInfo(type) == null)
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        assessments = mock(EmploymentAssessmentMapper.class); certificates = mock(TrainingCertificateMapper.class);
        people = mock(DisabledPersonMapper.class); grids = mock(GridScopeService.class);
        when(grids.requireSingleGridId()).thenReturn(100L);
        when(grids.getCurrentGridIds()).thenReturn(List.of(100L));
        person = new DisabledPersonPO(); person.setId(10L); person.setGridId(100L);
        when(people.selectById(10L)).thenReturn(person);
        when(people.selectAccessibleForUpdate(10L, 100L)).thenReturn(person);
        existing = new EmploymentAssessmentPO(); existing.setId(40L); existing.setPersonId(10L);
        existing.setEmploymentIntention("1"); existing.setOperatorId(16L);
        when(assessments.selectById(40L)).thenReturn(existing);
        when(assessments.selectForUpdate(40L, 10L)).thenReturn(existing);
        when(assessments.insert(any(EmploymentAssessmentPO.class))).thenAnswer(a -> {
            ((EmploymentAssessmentPO) a.getArgument(0)).setId(41L); return 1;
        });
        when(assessments.updateById(any(EmploymentAssessmentPO.class))).thenReturn(1);
        when(assessments.deleteById(40L)).thenReturn(1);
        certificate = new TrainingCertificatePO(); certificate.setId(50L); certificate.setPersonId(10L);
        certificate.setTrainingRecordId(1L); certificate.setCertificateNo("REAL-CERTIFICATE");
        when(certificates.selectById(50L)).thenReturn(certificate);
        when(certificates.selectList(any())).thenReturn(List.of(certificate));
        service = new EmploymentAssessmentServiceImpl();
        ReflectionTestUtils.setField(service, "employmentAssessmentMapper", assessments);
        ReflectionTestUtils.setField(service, "disabledPersonMapper", people);
        ReflectionTestUtils.setField(service, "gridScopeService", grids);
        certificateService = new TrainingCertificateServiceImpl();
        ReflectionTestUtils.setField(certificateService, "trainingCertificateMapper", certificates);
        ReflectionTestUtils.setField(certificateService, "disabledPersonMapper", people);
        ReflectionTestUtils.setField(certificateService, "gridScopeService", grids);
        LoginUser user = new LoginUser(18L, "test-business", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_BUSINESS")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    private EmploymentAssessmentSaveDTO dto() {
        EmploymentAssessmentSaveDTO dto = new EmploymentAssessmentSaveDTO(); dto.setPersonId(10L);
        dto.setExistingSkills("真实登记的经验"); return dto;
    }

    @Test
    void createUsesServerOperatorAndDateAndAllowsHistoricalAssessments() {
        assertEquals(41L, service.create(dto())); service.create(dto());
        ArgumentCaptor<EmploymentAssessmentPO> rows = ArgumentCaptor.forClass(EmploymentAssessmentPO.class);
        verify(assessments, times(2)).insert(rows.capture());
        for (EmploymentAssessmentPO row : rows.getAllValues()) {
            assertEquals(18L, row.getOperatorId()); assertEquals(LocalDate.now(), row.getAssessmentDate());
            assertEquals("3", row.getEmploymentIntention()); assertEquals(0, row.getDeleted());
        }
    }

    @Test
    void updateKeepsRealPersonAndUsesCurrentOperator() {
        service.update(40L, dto());
        ArgumentCaptor<EmploymentAssessmentPO> row = ArgumentCaptor.forClass(EmploymentAssessmentPO.class);
        verify(assessments).updateById(row.capture());
        assertEquals(40L, row.getValue().getId()); assertNull(row.getValue().getPersonId());
        assertEquals(18L, row.getValue().getOperatorId());
        assertNull(row.getValue().getCreateTime());
        EmploymentAssessmentSaveDTO moved = dto(); moved.setPersonId(99L);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.update(40L, moved)).getCode());
    }

    @Test
    void accessUsesPersistedPersonForDetailsWritesAndCertificates() {
        person.setGridId(200L);
        when(people.selectAccessibleForUpdate(10L, 100L)).thenReturn(null);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.detail(40L)).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.update(40L, dto())).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.delete(40L)).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(dto())).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> certificateService.detail(50L)).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> certificateService.byPerson(10L)).getCode());
        verify(assessments, never()).updateById(any(EmploymentAssessmentPO.class));
        verify(assessments, never()).deleteById(anyLong());
    }

    @Test
    void adminCanViewGlobalDataWhileBusinessCannotGuessOtherRecords() {
        person.setGridId(200L); when(grids.isAdmin()).thenReturn(true);
        assertEquals(10L, service.detail(40L).getPersonId());
        assertEquals("REAL-CERTIFICATE", certificateService.detail(50L).getCertificateNo());
        assertEquals(1, certificateService.byPerson(10L).size());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.detail(999L)).getCode());
        when(grids.isAdmin()).thenReturn(false);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.detail(999L)).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void paginationScopesRowsAndCountsByRealPersonGrid() {
        when(assessments.selectPage(any(Page.class), any())).thenAnswer(a -> {
            LambdaQueryWrapper<EmploymentAssessmentPO> query = a.getArgument(1);
            assertTrue(query.getSqlSegment().contains("p.id = employment_assessment.person_id"));
            assertTrue(query.getSqlSegment().contains("p.deleted = 0 AND p.grid_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(100L));
            Page<EmploymentAssessmentPO> page = a.getArgument(0);
            page.setTotal(1); page.setRecords(List.of(existing)); return page;
        });
        when(certificates.selectPage(any(Page.class), any())).thenAnswer(a -> {
            LambdaQueryWrapper<TrainingCertificatePO> query = a.getArgument(1);
            assertTrue(query.getSqlSegment().contains("p.id = training_certificate.person_id"));
            assertTrue(query.getSqlSegment().contains("p.deleted = 0 AND p.grid_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(100L));
            Page<TrainingCertificatePO> page = a.getArgument(0);
            page.setTotal(1); page.setRecords(List.of(certificate)); return page;
        });
        assertEquals(1, service.page(new EmploymentAssessmentQueryDTO()).getTotal());
        assertEquals(1, certificateService.page(new TrainingCertificateQueryDTO()).getTotal());
    }

    @Test
    void unassignedGridReturnsEmptyAndMultipleGridsAreRejected() {
        when(grids.getCurrentGridIds()).thenReturn(List.of());
        assertEquals(0, service.page(new EmploymentAssessmentQueryDTO()).getTotal());
        assertEquals(0, certificateService.page(new TrainingCertificateQueryDTO()).getTotal());
        verify(assessments, never()).selectPage(any(), any());
        when(grids.getCurrentGridIds()).thenReturn(List.of(100L, 200L));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.page(new EmploymentAssessmentQueryDTO())).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> certificateService.page(new TrainingCertificateQueryDTO())).getCode());
    }

    @Test
    void deleteUsesExistingLogicalDeleteMapping() {
        service.delete(40L);
        verify(assessments).selectForUpdate(40L, 10L); verify(assessments).deleteById(40L);
        assertTrue(TableInfoHelper.getTableInfo(EmploymentAssessmentPO.class).isWithLogicDelete());
        assertFalse(TableInfoHelper.getTableInfo(TrainingCertificatePO.class).isWithLogicDelete());
    }

    @Test
    void invalidCodesAndOversizedTextAreRejectedWithoutDatabaseWrites() {
        EmploymentAssessmentSaveDTO dto = dto(); dto.setEmploymentIntention("有意愿");
        assertEquals(400, assertThrows(BusinessException.class, () -> service.create(dto)).getCode());
        dto.setEmploymentIntention("1"); dto.setExistingSkills("甲".repeat(22000));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.create(dto)).getCode());
        verify(assessments, never()).insert(any(EmploymentAssessmentPO.class));
    }

    @Test
    void missingAuthenticatedOperatorIsNotAccepted() {
        SecurityContextHolder.clearContext();
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(dto())).getCode());
        verify(assessments, never()).insert(any(EmploymentAssessmentPO.class));
    }
}


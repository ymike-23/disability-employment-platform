package com.yzr.disability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.entity.dto.*;
import com.yzr.disability.entity.po.*;
import com.yzr.disability.entity.vo.TrainingRecordVO;
import com.yzr.disability.mapper.*;
import com.yzr.disability.service.*;
import com.yzr.disability.service.impl.TrainingRecordServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 纯Mockito检查，不启动应用，不连接数据库、Redis或模型服务。 */
class TrainingOutcomeUnitTest {
    TrainingRecordMapper records;
    TrainingCertificateMapper certificates;
    DisabledPersonMapper people;
    SysDictMapper dictionaries;
    ResumeService resumes;
    GridScopeService grids;
    TrainingRecordServiceImpl service;
    TrainingRecordPO record;
    TrainingPlanPO plan;
    AtomicReference<TrainingCertificatePO> issued;

    @BeforeEach
    void prepare() {
        for (Class<?> type : new Class<?>[]{TrainingRecordPO.class, TrainingCertificatePO.class, SysDictPO.class})
            if (TableInfoHelper.getTableInfo(type) == null)
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        records = mock(TrainingRecordMapper.class); certificates = mock(TrainingCertificateMapper.class);
        people = mock(DisabledPersonMapper.class); dictionaries = mock(SysDictMapper.class);
        resumes = mock(ResumeService.class); grids = mock(GridScopeService.class);
        service = new TrainingRecordServiceImpl();
        ReflectionTestUtils.setField(service, "trainingRecordMapper", records);
        ReflectionTestUtils.setField(service, "trainingCertificateMapper", certificates);
        ReflectionTestUtils.setField(service, "disabledPersonMapper", people);
        ReflectionTestUtils.setField(service, "sysDictMapper", dictionaries);
        ReflectionTestUtils.setField(service, "resumeService", resumes);
        ReflectionTestUtils.setField(service, "gridScopeService", grids);
        when(grids.requireSingleGridId()).thenReturn(100L);
        DisabledPersonPO person = new DisabledPersonPO(); person.setId(10L); person.setGridId(100L);
        when(people.selectAccessibleForUpdate(10L, 100L)).thenReturn(person);
        record = new TrainingRecordPO(); record.setId(1L); record.setPersonId(10L);
        record.setPlanId(20L); record.setStatus("1"); record.setDeleted(0);
        when(records.selectById(1L)).thenReturn(record);
        when(records.selectForUpdate(1L, 10L, 20L)).thenReturn(record);
        plan = new TrainingPlanPO(); plan.setId(20L); plan.setCategory("2"); plan.setDeleted(0);
        plan.setPlanName("计算机办公技能培训"); plan.setPublishStatus("1"); plan.setStatus("1");
        plan.setStartDate(LocalDate.now().minusDays(10)); plan.setEndDate(LocalDate.now().minusDays(1));
        plan.setCapacity(10); plan.setEnrolledCount(1);
        when(records.selectPlanForUpdate(20L)).thenReturn(plan);
        when(records.update(nullable(TrainingRecordPO.class), any())).thenReturn(1);
        when(records.decrementEnrollment(eq(20L), any())).thenReturn(1);
        issued = new AtomicReference<>();
        when(certificates.selectByRecordForUpdate(1L)).thenAnswer(a -> issued.get());
        when(certificates.insert(any(TrainingCertificatePO.class))).thenAnswer(a -> {
            TrainingCertificatePO certificate = a.getArgument(0);
            certificate.setId(50L); issued.set(certificate); return 1;
        });
        when(dictionaries.selectOne(any())).thenAnswer(a -> {
            SysDictPO dict = new SysDictPO(); dict.setDictValue("5"); return dict;
        });
    }

    private TrainingOutcomeSaveDTO passed() {
        TrainingOutcomeSaveDTO dto = new TrainingOutcomeSaveDTO();
        dto.setResult("1"); dto.setAcquiredSkills("Excel；文字录入");
        return dto;
    }

    private TrainingRecordSaveDTO status(String value) {
        TrainingRecordSaveDTO dto = new TrainingRecordSaveDTO();
        dto.setPersonId(10L); dto.setPlanId(20L); dto.setStatus(value);
        return dto;
    }

    private void futurePlan() {
        plan.setStartDate(LocalDate.now().plusDays(1)); plan.setEndDate(LocalDate.now().plusDays(10));
    }

    @Test
    void passingCreatesOneCertificateAndMergesOnlyConfirmedSkills() {
        TrainingRecordVO result = service.registerOutcome(1L, passed());
        assertEquals("1", result.getResult()); assertEquals("3", result.getStatus());
        assertEquals(LocalDate.now(), result.getAssessmentDate());
        assertEquals(issued.get().getCertificateNo(), result.getCertificateNo());
        assertEquals("计算机办公技能培训结业证书", issued.get().getCertificateName());
        assertEquals(10L, issued.get().getPersonId()); assertEquals(1L, issued.get().getTrainingRecordId());
        service.registerOutcome(1L, passed());
        verify(certificates, times(1)).insert(any(TrainingCertificatePO.class));
        verify(resumes, times(2)).mergeConfirmedTrainingSkills(10L, "Excel；文字录入");
        verify(records, never()).incrementEnrollment(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2", "3"})
    void nonPassingResultsNeverIssueOrMerge(String value) {
        record.setCertificateNo("历史手填编号");
        TrainingOutcomeSaveDTO dto = new TrainingOutcomeSaveDTO(); dto.setResult(value);
        assertEquals(value, service.registerOutcome(1L, dto).getResult());
        assertNull(record.getCertificateNo());
        verify(certificates, never()).insert(any(TrainingCertificatePO.class)); verifyNoInteractions(resumes);
    }

    @Test
    void passingWithoutConfirmedSkillsIssuesButDoesNotInventSkills() {
        TrainingOutcomeSaveDTO dto = new TrainingOutcomeSaveDTO(); dto.setResult("1");
        service.registerOutcome(1L, dto);
        verify(certificates).insert(any(TrainingCertificatePO.class)); verifyNoInteractions(resumes);
    }

    @Test
    void rehabilitationDoesNotCreateCertificateOrChangeResume() {
        plan.setCategory("1");
        TrainingOutcomeSaveDTO dto = new TrainingOutcomeSaveDTO();
        dto.setRehabEffect("有所改善"); dto.setImprovement("按记录完成训练");
        TrainingRecordVO result = service.registerOutcome(1L, dto);
        assertNull(result.getAcquiredSkills()); assertNull(result.getCertificateNo());
        assertNull(result.getResult()); assertEquals("有所改善", result.getRehabEffect());
        verifyNoInteractions(resumes); verify(certificates, never()).insert(any(TrainingCertificatePO.class));
    }

    @Test
    void rehabilitationRejectsEmploymentFields() {
        plan.setCategory("1");
        assertEquals(400, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed())).getCode());
    }

    @Test
    void cancelledEnrollmentCannotReceiveOutcome() {
        record.setStatus("5");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed())).getCode());
        verifyNoInteractions(resumes, certificates);
    }

    @Test
    void storedFinishedFlagDoesNotOverrideActualEndDate() {
        plan.setEndDate(LocalDate.now()); plan.setStatus("3");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed())).getCode());
        verifyNoInteractions(resumes, certificates);
    }

    @Test
    void unPublishedOrCancelledPlansCannotReceiveOutcome() {
        plan.setPublishStatus("0");
        assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed()));
        plan.setPublishStatus("1"); plan.setStatus("4");
        assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed()));
    }

    @Test
    void outcomeDateMustBeWithinAllowedRange() {
        TrainingOutcomeSaveDTO dto = passed(); dto.setAssessmentDate(LocalDate.now().plusDays(1));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, dto)).getCode());
        dto.setAssessmentDate(plan.getEndDate().minusDays(1));
        assertThrows(BusinessException.class, () -> service.registerOutcome(1L, dto));
    }

    @Test
    void outOfGridIsRejectedBeforeCertificateOrResumeWork() {
        when(people.selectAccessibleForUpdate(10L, 100L)).thenReturn(null);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed())).getCode());
        verifyNoInteractions(resumes, certificates);
    }

    @Test
    void issuedOutcomeCannotBeDowngradedOrLoseConfirmedSkills() {
        service.registerOutcome(1L, passed());
        TrainingOutcomeSaveDTO failed = new TrainingOutcomeSaveDTO(); failed.setResult("2");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.registerOutcome(1L, failed)).getCode());
        TrainingOutcomeSaveDTO reduced = passed(); reduced.setAcquiredSkills("Excel");
        assertThrows(BusinessException.class, () -> service.registerOutcome(1L, reduced));
        verify(certificates, times(1)).insert(any(TrainingCertificatePO.class));
    }

    @Test
    void skillFailurePropagatesToOuterTransaction() throws Exception {
        doThrow(new BusinessException(409, "摘要更新失败")).when(resumes).mergeConfirmedTrainingSkills(anyLong(), anyString());
        assertThrows(BusinessException.class, () -> service.registerOutcome(1L, passed()));
        Transactional transaction = TrainingRecordServiceImpl.class
                .getMethod("registerOutcome", Long.class, TrainingOutcomeSaveDTO.class).getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(java.util.Arrays.asList(transaction.rollbackFor()).contains(Exception.class));
    }

    @Test
    void cancellationBeforeStartReleasesOneSeat() {
        futurePlan();
        service.update(1L, status("5"));
        verify(records).decrementEnrollment(eq(20L), any());
    }

    @Test
    void repeatedCancellationCannotReleaseAgain() {
        futurePlan(); record.setStatus("5");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.update(1L, status("5"))).getCode());
        verify(records, never()).decrementEnrollment(anyLong(), any());
    }

    @Test
    void cancellationAndDeletionAfterStartAreRejected() {
        plan.setStartDate(LocalDate.now());
        assertThrows(BusinessException.class, () -> service.update(1L, status("5")));
        assertThrows(BusinessException.class, () -> service.delete(1L));
        verify(records, never()).decrementEnrollment(anyLong(), any());
    }

    @Test
    void registeredPendingResultAlsoBlocksCancellation() {
        futurePlan(); record.setResult("0");
        assertThrows(BusinessException.class, () -> service.update(1L, status("5")));
        verify(records, never()).decrementEnrollment(anyLong(), any());
    }

    @Test
    void legacyUpdateCannotBypassOutcomeValidation() {
        futurePlan(); TrainingRecordSaveDTO dto = status("1"); dto.setScore(new java.math.BigDecimal("90"));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.update(1L, dto)).getCode());
        verify(records, never()).update(nullable(TrainingRecordPO.class), any());
    }

    @Test
    void restoreAfterStartCannotConsumeSeat() {
        record.setStatus("5");
        when(dictionaries.selectOne(any())).thenAnswer(a -> {
            SysDictPO dict = new SysDictPO(); dict.setDictValue("1"); return dict;
        });
        assertThrows(BusinessException.class, () -> service.update(1L, status("1")));
        verify(records, never()).incrementEnrollment(anyLong(), any());
    }
}


package com.yzr.disability;

import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.EmploymentMatchDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.vo.*;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.ResumeMapper;
import com.yzr.disability.service.*;
import com.yzr.disability.service.ai.MatchTextSanitizer;
import com.yzr.disability.service.ai.QwenMatchClient;
import com.yzr.disability.service.impl.EmploymentMatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 纯单元检查，不启动Spring、不连接数据库、Redis或千问。 */
class EmploymentMatchResultTest {
    private final ResumeService resumes = mock(ResumeService.class);
    private final EmploymentService employment = mock(EmploymentService.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final DisabledPersonMapper people = mock(DisabledPersonMapper.class);
    private final DictService dictionaries = mock(DictService.class);
    private final QwenMatchClient qwen = mock(QwenMatchClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private EmploymentMatchServiceImpl service;

    @BeforeEach
    void prepare() {
        var resume = new ResumeVO();
        resume.setId(3L); resume.setPersonId(3L); resume.setEducation("6");
        resume.setJobIntention("搬货员"); resume.setExpectedSalary("3000");
        when(resumes.detail(3L)).thenReturn(resume);
        var person = new DisabledPersonPO(); person.setId(3L);
        when(people.selectById(3L)).thenReturn(person);
        when(employment.ongoingJobIds(3L)).thenReturn(Set.of());
        when(resumeMapper.selectCompletedEmploymentTrainingNames(anyLong(), anyString(), anyString())).thenReturn(List.of());
        when(dictionaries.list("education")).thenReturn(List.of(new DictOptionVO("6", "本科"), new DictOptionVO("2", "高中")));
        var jobs = new ArrayList<JobVO>();
        for (long id = 1; id <= 6; id++) {
            var job = new JobVO(); job.setId(id); job.setJobName(id == 3 ? "搬货员" : "收银员");
            job.setCompanyName("样例企业"); job.setRequirement("按要求完成工作");
            job.setEducationRequirement("2"); job.setSalary("3000-4000"); jobs.add(job);
        }
        when(employment.candidateJobs(any())).thenReturn(new PageResult<>(jobs, 6, 1, 20, 1));
        service = new EmploymentMatchServiceImpl(resumes, employment, resumeMapper, people, dictionaries, qwen);
    }

    private String response() {
        var items = new ArrayList<String>();
        int[] intentions = {60, 70, 95, 90, 50, 50};
        for (int i = 0; i < intentions.length; i++)
            items.add("""
                    {"ref":"J%d","scores":{"skills":null,"intention":%d,"education":100,"salary":100},
                    "reasons":["求职意向与岗位要求相符"],"missingInfo":["未提供技能摘要"]}
                    """.formatted(i + 1, intentions[i]));
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private EmploymentMatchVO match(String response) {
        when(qwen.analyze(anyMap())).thenReturn(json.readTree(response));
        var request = new EmploymentMatchDTO(); request.setResumeId(3L);
        return service.match(request);
    }

    @Test
    void redactsSensitiveNotesWithoutLosingScoresOrStableRanking() {
        var response = response().replaceFirst("未提供技能摘要", "需要核实联系人电话")
                .replaceFirst("求职意向与岗位要求相符", "姓名与地址需核实");
        var result = match(response);
        assertEquals("AI", result.mode());
        assertEquals(List.of(3L, 4L, 2L, 1L, 5L, 6L), result.items().stream().map(EmploymentMatchVO.Item::jobId).toList());
        assertEquals(new BigDecimal("97.27"), result.items().getFirst().totalScore());
        var redacted = result.items().stream().filter(item -> item.jobId() == 1L).findFirst().orElseThrow();
        assertTrue(redacted.reasons().isEmpty());
        assertTrue(redacted.missingInfo().stream().anyMatch(info -> info.contains("已省略")));
        assertTrue(result.items().stream().allMatch(item -> item.scores().skills() == null));
        assertFalse(json.writeValueAsString(result).contains("联系人电话"));
        assertFalse(json.writeValueAsString(result).contains("姓名与地址"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"outOfRange", "missingScore", "inventedSkill", "duplicateRef", "missingRef", "noReason", "longText", "nonText"})
    void malformedOrUnsupportedAnalysisStillFallsBack(String defect) {
        String response = switch (defect) {
            case "outOfRange" -> response().replaceFirst("\"salary\":100", "\"salary\":101");
            case "missingScore" -> response().replaceFirst("\"skills\":null,", "");
            case "inventedSkill" -> response().replaceFirst("\"skills\":null", "\"skills\":80");
            case "duplicateRef" -> response().replace("\"ref\":\"J6\"", "\"ref\":\"J1\"");
            case "missingRef" -> response().replace("\"ref\":\"J6\"", "\"ref\":\"J999\"");
            case "noReason" -> response().replaceFirst("\"reasons\":\\[\"[^\"]+\"\\]", "\"reasons\":[]");
            case "longText" -> response().replaceFirst("未提供技能摘要", "待".repeat(161));
            case "nonText" -> response().replaceFirst("\"未提供技能摘要\"", "42");
            default -> throw new IllegalArgumentException();
        };
        var result = match(response);
        assertEquals("MANUAL", result.mode());
        assertEquals(6, result.items().size());
        assertTrue(result.items().stream().allMatch(item -> item.totalScore() == null && item.reasons().isEmpty()));
    }

    @Test
    void missingAllScoresNeverCreatesAFallbackScore() {
        var result = match(response().replaceAll("\"(intention|education|salary)\":\\d+", "\"$1\":null"));
        assertEquals("AI", result.mode());
        assertTrue(result.items().stream().allMatch(item -> item.totalScore() == null));
    }

    @Test
    void noCandidatesDoesNotCallQwen() {
        when(employment.candidateJobs(any())).thenReturn(PageResult.empty());
        var request = new EmploymentMatchDTO(); request.setResumeId(3L);
        assertTrue(service.match(request).items().isEmpty());
        verifyNoInteractions(qwen);
    }

    @Test
    void deniedResumeIsNotConvertedToManualMode() {
        when(resumes.detail(3L)).thenThrow(new BusinessException(403, "无权访问"));
        var request = new EmploymentMatchDTO(); request.setResumeId(3L);
        assertThrows(BusinessException.class, () -> service.match(request));
        verifyNoInteractions(qwen);
    }

    @Test
    void sensitiveDataAndAmbiguousTextRemainFiltered() {
        var sanitizer = new MatchTextSanitizer(List.of("档案姓名"));
        assertNull(sanitizer.clean("档案姓名", 160));
        assertNull(sanitizer.clean("联系电话13800138000", 160));
        assertNull(sanitizer.clean("呼和浩特市新城区街道", 160));
        assertNull(sanitizer.clean("身体健康情况需核实", 160));
        assertNull(sanitizer.clean("岗位薪资区间覆盖期望薪资", 160));
    }
}

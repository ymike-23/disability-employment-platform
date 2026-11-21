package com.yzr.disability.service.impl;

import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.EmploymentMatchDTO;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.vo.DictOptionVO;
import com.yzr.disability.entity.vo.EmploymentMatchVO;
import com.yzr.disability.entity.vo.EmploymentMatchVO.Item;
import com.yzr.disability.entity.vo.EmploymentMatchVO.Scores;
import com.yzr.disability.entity.vo.JobVO;
import com.yzr.disability.entity.vo.ResumeVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.ResumeMapper;
import com.yzr.disability.service.DictService;
import com.yzr.disability.service.EmploymentMatchService;
import com.yzr.disability.service.EmploymentService;
import com.yzr.disability.service.ResumeService;
import com.yzr.disability.service.TrainingRecordService;
import com.yzr.disability.service.ai.MatchTextSanitizer;
import com.yzr.disability.service.ai.QwenMatchClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// AI岗位匹配核心：查询真实简历和候选岗位，调用千问分析，校验评分后返回结果，不创建推荐记录。
@Service
public class EmploymentMatchServiceImpl implements EmploymentMatchService {
    // 与简历汇总查询一致：sys_dict.training_category的就业类dict_value。
    private static final String EMPLOYMENT_CATEGORY = "2";
    private static final List<String> DIMENSIONS = List.of("skills", "intention", "education", "salary");
    // 权重依次对应技能、求职意向、学历、薪资；最终总分由后端计算。
    private static final int[] WEIGHTS = {45, 30, 10, 15};
    private static final String[] MISSING_LABELS = {"技能与岗位要求", "求职意向", "学历要求", "薪资期望"};

    private final ResumeService resumeService;
    private final EmploymentService employmentService;
    private final ResumeMapper resumeMapper;
    private final DisabledPersonMapper personMapper;
    private final DictService dictService;
    private final QwenMatchClient qwen;

    public EmploymentMatchServiceImpl(ResumeService resumeService, EmploymentService employmentService,
                                      ResumeMapper resumeMapper, DisabledPersonMapper personMapper,
                                      DictService dictService, QwenMatchClient qwen) {
        this.resumeService = resumeService;
        this.employmentService = employmentService;
        this.resumeMapper = resumeMapper;
        this.personMapper = personMapper;
        this.dictService = dictService;
        this.qwen = qwen;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public EmploymentMatchVO match(EmploymentMatchDTO request) {
        if (request == null || request.getResumeId() == null || request.getResumeId() <= 0)
            throw new BusinessException(400, "简历ID必须大于0");
        // 复用简历真实人员的网格权限，授权及数据库错误不进入模型降级分支。
        ResumeVO resume = resumeService.detail(request.getResumeId());
        Set<Long> ongoingJobs = employmentService.ongoingJobIds(resume.getPersonId());
        List<JobVO> jobs = candidates(ongoingJobs);
        if (jobs.isEmpty()) return manual(jobs, "暂无可供匹配的有效候选岗位，未调用AI");

        // 整理匹配所需资料并清理敏感内容，外发数据仅保留学历、意向、技能、薪资和培训名称。
        DisabledPersonPO person = personMapper.selectById(resume.getPersonId());
        if (person == null) throw new BusinessException(409, "人员档案已变化，请刷新后重试");
        List<String> privateValues = new ArrayList<>(Arrays.asList(person.getName(), person.getIdCard(),
                person.getPhone(), person.getAddress(), resume.getName(), resume.getPhone(), resume.getAddress()));
        if (person.getBirthDate() != null) {
            privateValues.add(person.getBirthDate().toString());
            privateValues.add(person.getBirthDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        jobs.forEach(job -> privateValues.add(job.getCompanyName()));
        MatchTextSanitizer sanitizer = new MatchTextSanitizer(privateValues);
        List<DictOptionVO> educationOptions = dictService.list("education");
        String education = educationLabel(resume.getEducation(), educationOptions, sanitizer);
        String intention = sanitizer.clean(resume.getJobIntention(), 255);
        String skills = sanitizer.clean(resume.getSkillsSummary(), 2000);
        String salary = sanitizer.clean(resume.getExpectedSalary(), 80);
        List<String> trainingNames = resumeMapper.selectCompletedEmploymentTrainingNames(resume.getPersonId(),
                        TrainingRecordService.STATUS_COMPLETED, EMPLOYMENT_CATEGORY).stream()
                .map(name -> sanitizer.clean(name, 100)).filter(Objects::nonNull).distinct().limit(10).toList();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("education", education);
        profile.put("jobIntention", intention);
        profile.put("skillsSummary", skills);
        profile.put("expectedSalary", salary);
        profile.put("completedEmploymentTrainingNames", trainingNames);

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < jobs.size(); i++) {
            JobVO job = jobs.get(i);
            String name = sanitizer.clean(job.getJobName(), 100);
            String requirement = sanitizer.clean(job.getRequirement(), 1500);
            String requiredEducation = educationLabel(job.getEducationRequirement(), educationOptions, sanitizer);
            String offeredSalary = sanitizer.clean(job.getSalary(), 80);
            // 根据双方资料判断各维度能否评分，资料不足时要求模型返回null。
            Map<String, Boolean> available = Map.of(
                    "skills", (skills != null || !trainingNames.isEmpty()) && requirement != null,
                    "intention", intention != null && (name != null || requirement != null),
                    "education", education != null && requiredEducation != null,
                    "salary", comparableSalary(salary) && comparableSalary(offeredSalary));
            String ref = "J" + (i + 1);
            // 严格白名单投影；不序列化VO、人员实体、真实ID、企业名称或岗位备注。
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("ref", ref);
            input.put("jobName", name);
            input.put("requirement", requirement);
            input.put("educationRequirement", requiredEducation);
            input.put("salary", offeredSalary);
            input.put("availableDimensions", available);
            candidates.put(ref, new Candidate(job, input, available));
        }

        // 外发前再次确认当前网格归属；所有查询均为普通读，不持有人员或应聘行锁。
        resumeService.detail(request.getResumeId());
        EmploymentMatchVO result;
        try {
            JsonNode analysis = qwen.analyze(Map.of("resume", profile,
                    "jobs", candidates.values().stream().map(Candidate::input).toList()));
            result = validateResult(analysis, candidates, sanitizer);
        } catch (QwenMatchClient.UnavailableException e) {
            result = manual(jobs, e.getMessage());
        } catch (InvalidMatchResult e) {
            result = manual(jobs, "千问返回的匹配内容未通过校验，请人工选择候选岗位");
        }
        // 模型调用期间发生网格归属变化时，也不向失去权限的调用者返回资料。
        resumeService.detail(request.getResumeId());
        return result;
    }

    // 复用有效招聘岗位查询，排除已有未结束应聘批次的岗位，最多保留20个候选。
    private List<JobVO> candidates(Set<Long> ongoingJobs) {
        JobQueryDTO query = new JobQueryDTO();
        query.setSize(20L);
        List<JobVO> jobs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long current = 1; jobs.size() < 20; current++) {
            query.setCurrent(current);
            PageResult<JobVO> page = employmentService.candidateJobs(query);
            for (JobVO job : page.getRecords()) {
                if (!ongoingJobs.contains(job.getId()) && seen.add(job.getId())) jobs.add(job);
                if (jobs.size() == 20) break;
            }
            if (page.getRecords().isEmpty() || current >= page.getPages()) break;
        }
        return jobs;
    }

    // 校验模型返回的岗位编号、分数和说明，使用数据库中的真实岗位信息组装结果并排序。
    private EmploymentMatchVO validateResult(JsonNode root, Map<String, Candidate> candidates,
                                             MatchTextSanitizer sanitizer) {
        JsonNode items = root == null ? null : root.get("items");
        if (root == null || !root.isObject() || items == null || !items.isArray()
                || items.size() > 40) throw new InvalidMatchResult();
        Map<String, JsonNode> analyses = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            JsonNode ref = item.get("ref");
            if (!item.isObject() || ref == null || !ref.isTextual() || ref.asText().length() > 20
                    || !seen.add(ref.asText())) throw new InvalidMatchResult();
            if (candidates.containsKey(ref.asText())) analyses.put(ref.asText(), item);
        }
        if (analyses.size() != candidates.size()) throw new InvalidMatchResult();
        List<Item> result = new ArrayList<>();
        for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
            Candidate candidate = entry.getValue();
            JsonNode item = analyses.get(entry.getKey());
            JsonNode scoreNode = item.get("scores");
            if (scoreNode == null || !scoreNode.isObject()) throw new InvalidMatchResult();
            List<BigDecimal> scores = new ArrayList<>();
            for (String dimension : DIMENSIONS)
                scores.add(score(scoreNode.get(dimension), candidate.available().get(dimension)));
            List<String> reasons = textList(item.get("reasons"), 3, sanitizer);
            List<String> hints = textList(item.get("missingInfo"), 5, sanitizer);
            Set<String> missing = new LinkedHashSet<>(hints);
            BigDecimal total = weightedScore(scores);
            if (total != null && item.get("reasons").isEmpty()) throw new InvalidMatchResult();
            if (reasons.size() < item.get("reasons").size() || hints.size() < item.get("missingInfo").size())
                missing.add("部分AI说明未通过安全检查，已省略，请结合岗位详情人工核实");
            for (int i = 0; i < scores.size(); i++)
                if (scores.get(i) == null) missing.add(MISSING_LABELS[i] + "尚待人工核实");
            JobVO job = candidate.job();
            result.add(new Item(job.getId(), job.getJobName(), job.getCompanyName(), total,
                    new Scores(scores.get(0), scores.get(1), scores.get(2), scores.get(3)),
                    reasons, List.copyOf(missing)));
        }
        // List.sort为稳定排序，同分保留后端候选查询顺序，不依赖模型的排序。
        result.sort(Comparator.comparing(Item::totalScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return new EmploymentMatchVO("AI", "本次候选岗位的参考适配分析，分数不代表录用概率，请核实后确认推荐", result);
    }

    private BigDecimal score(JsonNode node, boolean available) {
        if (node == null) throw new InvalidMatchResult();
        if (node.isNull()) return null;
        if (!available || !node.isNumber()) throw new InvalidMatchResult();
        BigDecimal value;
        try { value = node.decimalValue(); }
        catch (RuntimeException e) { throw new InvalidMatchResult(); }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0
                || value.scale() > 6 || value.precision() > 9) throw new InvalidMatchResult();
        return value;
    }

    // 只对非null维度计算加权平均并保留两位小数，全部缺失时总分也为null。
    private BigDecimal weightedScore(List<BigDecimal> scores) {
        BigDecimal sum = BigDecimal.ZERO;
        int weight = 0;
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i) == null) continue;
            sum = sum.add(scores.get(i).multiply(BigDecimal.valueOf(WEIGHTS[i])));
            weight += WEIGHTS[i];
        }
        return weight == 0 ? null : sum.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP);
    }

    private List<String> textList(JsonNode node, int maxItems, MatchTextSanitizer sanitizer) {
        if (node == null || !node.isArray() || node.size() > maxItems) throw new InvalidMatchResult();
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new InvalidMatchResult();
            if (value.asText().isBlank() || value.asText().length() > 160) throw new InvalidMatchResult();
            String text = sanitizer.clean(value.asText(), 160);
            // 敏感说明整条省略，不连带丢弃已通过校验的评分，也不编造替代理由。
            if (text != null) result.add(text);
        }
        return result;
    }

    private String educationLabel(String value, List<DictOptionVO> options, MatchTextSanitizer sanitizer) {
        if (value == null) return null;
        return options.stream().filter(option -> value.equals(option.getValue()) || value.equals(option.getLabel()))
                .map(DictOptionVO::getLabel).map(label -> sanitizer.clean(label, 50))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private boolean comparableSalary(String value) {
        return value != null && value.matches(".*[0-9].*") && !value.contains("面议") && !value.contains("待定");
    }

    // AI不可用或没有候选时返回人工模式，保留真实候选岗位，不生成虚假分数和理由。
    private EmploymentMatchVO manual(List<JobVO> jobs, String message) {
        return new EmploymentMatchVO("MANUAL", message, jobs.stream()
                .map(job -> new Item(job.getId(), job.getJobName(), job.getCompanyName(), null,
                        new Scores(null, null, null, null), List.of(), List.of())).toList());
    }

    private record Candidate(JobVO job, Map<String, Object> input, Map<String, Boolean> available) {
    }

    private static class InvalidMatchResult extends RuntimeException {
    }
}

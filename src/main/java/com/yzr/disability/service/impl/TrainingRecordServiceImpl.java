package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.common.util.TrainingPlanState;
import com.yzr.disability.common.util.TrainingRecordState;
import com.yzr.disability.entity.dto.TrainingOutcomeSaveDTO;
import com.yzr.disability.entity.dto.TrainingRecordQueryDTO;
import com.yzr.disability.entity.dto.TrainingRecordSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.po.TrainingRecordPO;
import com.yzr.disability.entity.po.TrainingCertificatePO;
import com.yzr.disability.entity.vo.TrainingRecordVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.TrainingRecordMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.mapper.TrainingCertificateMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import com.yzr.disability.service.TrainingRecordService;
import com.yzr.disability.service.ResumeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingRecordServiceImpl implements TrainingRecordService {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String RECORD_ACCESS_MESSAGE = "培训记录不存在或无权访问";
    private static final String DUPLICATE_ENROLLMENT_MESSAGE = "该人员已报名该培训计划";
    private static final String STATUS_DICT_TYPE = "training_record_status";
    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            STATUS_ENROLLED, STATUS_IN_TRAINING, STATUS_COMPLETED, STATUS_NOT_PASSED);

    @Autowired
    private TrainingRecordMapper trainingRecordMapper;
    @Autowired
    private TrainingPlanMapper trainingPlanMapper;
    @Autowired
    private DisabledPersonMapper disabledPersonMapper;
    @Autowired
    private SysDictMapper sysDictMapper;
    @Autowired
    private GridScopeService gridScopeService;
    @Autowired
    private TrainingCertificateMapper trainingCertificateMapper;
    @Autowired
    private ResumeService resumeService;

    @Override
    public PageResult<TrainingRecordVO> page(TrainingRecordQueryDTO query) {
        LocalDate today = LocalDate.now();
        boolean admin = gridScopeService.isAdmin();
        Long gridId = null;
        if (!admin) {
            List<Long> gridIds = gridScopeService.getCurrentGridIds();
            if (gridIds.size() > 1) {
                throw new BusinessException(403, GridScopeService.MULTI_GRID_MESSAGE);
            }
            if (gridIds.isEmpty()) {
                return PageResult.empty();
            }
            gridId = gridIds.get(0);
            if (query.getPersonId() != null) {
                requirePerson(query.getPersonId(), false, "档案不存在或无权访问");
            }
        }
        validateStatus(query.getStatus());
        if (query.getResult() != null && !Set.of(RESULT_PENDING, RESULT_PASSED, RESULT_FAILED, RESULT_ABSENT).contains(query.getResult()))
            throw new BusinessException(400, "考核结果必须为0、1、2或3");
        LambdaQueryWrapper<TrainingRecordPO> wrapper = new LambdaQueryWrapper<>();
        if (!admin) {
            // 用记录真实人员关系限定分页和总数，不按请求中的人员推导授权。
            wrapper.exists("SELECT 1 FROM disabled_person p"
                    + " WHERE p.id = training_record.person_id AND p.deleted = 0 AND p.grid_id = {0}", gridId);
        }
        if (query.getPersonId() != null) {
            wrapper.eq(TrainingRecordPO::getPersonId, query.getPersonId());
        }
        if (query.getPlanId() != null) {
            wrapper.eq(TrainingRecordPO::getPlanId, query.getPlanId());
        }
        if (query.getStatus() != null) {
            wrapper.apply("(" + TrainingRecordState.STATUS_SQL + ") = {1}", today, query.getStatus());
        }
        if (RESULT_PENDING.equals(query.getResult()))
            wrapper.and(w -> w.eq(TrainingRecordPO::getResult, RESULT_PENDING).or().isNull(TrainingRecordPO::getResult));
        else if (query.getResult() != null)
            wrapper.eq(TrainingRecordPO::getResult, query.getResult());
        wrapper.orderByDesc(TrainingRecordPO::getCreateTime).orderByDesc(TrainingRecordPO::getId);
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getSize(), MAX_PAGE_SIZE);
        Page<TrainingRecordPO> result = trainingRecordMapper.selectPage(new Page<>(current, size), wrapper);
        List<Long> planIds = result.getRecords().stream().map(TrainingRecordPO::getPlanId).distinct().toList();
        Map<Long, TrainingPlanPO> plans = planIds.isEmpty() ? Map.of() : trainingPlanMapper.selectList(
                new LambdaQueryWrapper<TrainingPlanPO>().in(TrainingPlanPO::getId, planIds)).stream()
                .collect(Collectors.toMap(TrainingPlanPO::getId, Function.identity()));
        List<TrainingRecordVO> records = result.getRecords().stream()
                .map(record -> toVO(record, plans.get(record.getPlanId()), today)).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public TrainingRecordVO detail(Long id) {
        TrainingRecordPO record = requireRecord(id, false);
        return toVO(record, trainingPlanMapper.selectById(record.getPlanId()), LocalDate.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TrainingRecordSaveDTO dto) {
        rejectInlineOutcome(dto, null);
        requirePerson(dto.getPersonId(), true, "档案不存在或无权访问");
        TrainingPlanPO plan = requirePlan(dto.getPlanId(), false);
        String status = dto.getStatus() == null ? STATUS_ENROLLED : dto.getStatus();
        validateStatus(status);
        if (!isEffectiveEnrollment(status)) {
            throw new BusinessException(400, "新增培训记录必须使用有效报名状态，请通过修改已有记录取消报名");
        }
        // 仅已发布且处于报名中的计划可报名，并校验剩余名额。
        requireEnrollable(plan);
        // 同一人员与计划只允许一条记录，含已取消、已逻辑删除的记录；恢复报名请修改原记录。
        requireNoDuplicateEnrollment(dto.getPersonId(), dto.getPlanId());
        TrainingRecordPO po = new TrainingRecordPO();
        BeanUtils.copyProperties(dto, po);
        normalizeFields(po);
        po.setStatus(status);
        po.setEnrollDate(dto.getEnrollDate() == null ? LocalDate.now() : dto.getEnrollDate());
        po.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        po.setCreateTime(now);
        po.setUpdateTime(now);
        try {
            if (trainingRecordMapper.insert(po) != 1) {
                throw new BusinessException(409, "新增培训记录失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, DUPLICATE_ENROLLMENT_MESSAGE);
        }
        increaseEnrollment(plan.getId());
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TrainingRecordSaveDTO dto) {
        TrainingRecordPO existing = requireRecord(id, true);
        // 先按真实记录校验权限，再检查请求中的人员和计划是否一致。
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId())
                || !Objects.equals(existing.getPlanId(), dto.getPlanId())) {
            throw new BusinessException(400, "不允许修改培训记录所属人员或培训计划");
        }
        rejectInlineOutcome(dto, existing);
        validateStatus(dto.getStatus());
        String status = dto.getStatus() == null ? existing.getStatus() : dto.getStatus();
        boolean wasEffective = isEffectiveEnrollment(existing.getStatus());
        boolean effective = isEffectiveEnrollment(status);
        if (STATUS_CANCELLED.equals(dto.getStatus())) {
            if (STATUS_CANCELLED.equals(existing.getStatus()))
                throw new BusinessException(409, "报名已取消，不能重复取消");
            requireCancellationAllowed(existing, requirePlan(existing.getPlanId(), true));
        }
        if (wasEffective != effective) {
            TrainingPlanPO plan = requirePlan(existing.getPlanId(), !effective);
            if (effective) {
                if (hasOutcome(existing)) throw new BusinessException(409, "已登记成果的历史记录不能恢复报名");
                requireEnrollable(plan);
            }
        }
        TrainingRecordPO changes = new TrainingRecordPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setPersonId(null);
        changes.setPlanId(null);
        normalizeFields(changes);
        changes.setUpdateTime(LocalDateTime.now());
        LambdaUpdateWrapper<TrainingRecordPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TrainingRecordPO::getId, id)
                .eq(TrainingRecordPO::getPersonId, existing.getPersonId())
                .eq(TrainingRecordPO::getPlanId, existing.getPlanId());
        if (trainingRecordMapper.update(changes, wrapper) != 1) {
            throw new BusinessException(409, "培训记录已变更，请刷新后重试");
        }
        if (wasEffective && !effective) {
            decreaseEnrollment(existing.getPlanId());
        } else if (!wasEffective && effective) {
            increaseEnrollment(existing.getPlanId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TrainingRecordPO existing = requireRecord(id, true);
        requireCancellationAllowed(existing, requirePlan(existing.getPlanId(), true));
        boolean effective = isEffectiveEnrollment(existing.getStatus());
        if (effective) {
            requirePlan(existing.getPlanId(), true);
        }
        LambdaQueryWrapper<TrainingRecordPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingRecordPO::getId, id)
                .eq(TrainingRecordPO::getPersonId, existing.getPersonId())
                .eq(TrainingRecordPO::getPlanId, existing.getPlanId());
        // @TableLogic 转换为 deleted=1，计数更新失败时整个事务回滚。
        if (trainingRecordMapper.delete(wrapper) != 1) {
            throw new BusinessException(409, "培训记录已变更，请刷新后重试");
        }
        if (effective) {
            decreaseEnrollment(existing.getPlanId());
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TrainingRecordVO registerOutcome(Long id, TrainingOutcomeSaveDTO dto) {
        TrainingRecordPO record = requireRecord(id, true);
        requirePerson(record.getPersonId(), true, "人员不存在或无权访问");
        TrainingPlanPO plan = requirePlan(record.getPlanId(), false);
        LocalDate today = LocalDate.now();
        if (!TrainingPlanState.validDates(plan)
                || !TrainingPlanService.PUBLISH_PUBLISHED.equals(plan.getPublishStatus())
                || !TrainingPlanService.STATUS_ENDED.equals(TrainingPlanState.effectiveStatus(plan, today)))
            throw new BusinessException(409, "仅已发布且按实际日期结束的培训允许登记成果");
        if (!isEffectiveEnrollment(record.getStatus()))
            throw new BusinessException(409, "已取消或无效的报名记录不能登记培训成果");
        if (dto == null) throw new BusinessException(400, "成果参数不能为空");
        validateOutcomeText(dto);
        LocalDate date = dto.getAssessmentDate() == null
                ? (record.getAssessmentDate() == null ? today : record.getAssessmentDate()) : dto.getAssessmentDate();
        if (date.isAfter(today) || date.isBefore(plan.getEndDate()))
            throw new BusinessException(400, "考核日期不能早于培训结束日期或晚于当前日期");

        TrainingCertificatePO certificate = trainingCertificateMapper.selectByRecordForUpdate(id);
        if (certificate != null && !Objects.equals(certificate.getPersonId(), record.getPersonId()))
            throw new BusinessException(409, "证书所属人员与培训记录不一致，请核实历史数据");
        if ("2".equals(plan.getCategory())) {
            if (dto.getResult() == null || !Set.of(RESULT_PENDING, RESULT_PASSED, RESULT_FAILED, RESULT_ABSENT).contains(dto.getResult()))
                throw new BusinessException(400, "就业类考核结果必须为0、1、2或3");
            if (StringUtils.hasText(dto.getRehabEffect()) || StringUtils.hasText(dto.getImprovement()))
                throw new BusinessException(400, "就业类培训不能登记康复成果");
            String skills = dto.getAcquiredSkills() == null ? record.getAcquiredSkills() : normalizeText(dto.getAcquiredSkills());
            if (!RESULT_PASSED.equals(dto.getResult())) {
                if (StringUtils.hasText(dto.getAcquiredSkills()))
                    throw new BusinessException(400, "仅考核通过后才能登记确认技能");
                skills = null;
            }
            // 表中没有撤销字段及技能来源明细；已发证结果禁止降级或移除确认技能，避免留下失真的证书和简历。
            if (certificate != null && (!RESULT_PASSED.equals(dto.getResult())
                    || !skillTokens(skills).containsAll(skillTokens(record.getAcquiredSkills()))))
                throw new BusinessException(409, "已发证成果不能降级或移除已确认技能，请先核实证书处理方式");
            record.setResult(dto.getResult());
            if (dto.getScore() != null) record.setScore(dto.getScore());
            record.setAcquiredSkills(skills);
            record.setRehabEffect(null);
            record.setImprovement(null);
            if (RESULT_PASSED.equals(record.getResult())) {
                if (certificate == null) certificate = createCertificate(record, plan, today);
                record.setCertificateNo(certificate.getCertificateNo());
            } else {
                record.setCertificateNo(null);
            }
        } else if ("1".equals(plan.getCategory())) {
            if (certificate != null) throw new BusinessException(409, "康复类记录存在职业技能证书，请核实历史数据");
            if (dto.getResult() != null || dto.getScore() != null || StringUtils.hasText(dto.getAcquiredSkills()))
                throw new BusinessException(400, "康复类培训仅登记康复效果、改善情况、日期和备注");
            String effect = dto.getRehabEffect() == null ? record.getRehabEffect() : normalizeText(dto.getRehabEffect());
            String improvement = dto.getImprovement() == null ? record.getImprovement() : normalizeText(dto.getImprovement());
            if (!StringUtils.hasText(effect) && !StringUtils.hasText(improvement))
                throw new BusinessException(400, "请填写康复效果或改善情况");
            record.setResult(null);
            record.setScore(null);
            record.setAcquiredSkills(null);
            record.setCertificateNo(null);
            record.setRehabEffect(effect);
            record.setImprovement(improvement);
        } else {
            throw new BusinessException(400, "该培训类别不支持成果登记");
        }
        record.setAssessmentDate(date);
        record.setStatus(TrainingRecordState.effectiveStatus(record.getStatus(), plan, today));
        if (dto.getRemark() != null) record.setRemark(normalizeText(dto.getRemark()));
        record.setUpdateTime(LocalDateTime.now());
        LambdaUpdateWrapper<TrainingRecordPO> changes = new LambdaUpdateWrapper<>();
        changes.eq(TrainingRecordPO::getId, id).eq(TrainingRecordPO::getPersonId, record.getPersonId())
                .eq(TrainingRecordPO::getPlanId, record.getPlanId())
                .set(TrainingRecordPO::getStatus, record.getStatus())
                .set(TrainingRecordPO::getResult, record.getResult())
                .set(TrainingRecordPO::getScore, record.getScore())
                .set(TrainingRecordPO::getAssessmentDate, date)
                .set(TrainingRecordPO::getAcquiredSkills, record.getAcquiredSkills())
                .set(TrainingRecordPO::getRehabEffect, record.getRehabEffect())
                .set(TrainingRecordPO::getImprovement, record.getImprovement())
                .set(TrainingRecordPO::getCertificateNo, record.getCertificateNo())
                .set(TrainingRecordPO::getRemark, record.getRemark())
                .set(TrainingRecordPO::getUpdateTime, record.getUpdateTime());
        if (trainingRecordMapper.update(null, changes) != 1) throw new BusinessException(409, "成果登记失败");
        if ("2".equals(plan.getCategory()) && RESULT_PASSED.equals(record.getResult())
                && StringUtils.hasText(record.getAcquiredSkills()))
            resumeService.mergeConfirmedTrainingSkills(record.getPersonId(), record.getAcquiredSkills());
        return toVO(record, plan, today);
    }

    private TrainingCertificatePO createCertificate(TrainingRecordPO record, TrainingPlanPO plan, LocalDate today) {
        if (!StringUtils.hasText(plan.getPlanName())) throw new BusinessException(409, "培训计划名称配置异常");
        String name = plan.getPlanName().trim();
        if (name.codePointCount(0, name.length()) > 96) name = name.substring(0, name.offsetByCodePoints(0, 96));
        TrainingCertificatePO certificate = new TrainingCertificatePO();
        certificate.setCertificateNo("TC" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        certificate.setCertificateName(name + "结业证书");
        certificate.setIssueDate(today);
        certificate.setPersonId(record.getPersonId());
        certificate.setTrainingRecordId(record.getId());
        certificate.setCreateTime(LocalDateTime.now());
        try {
            if (trainingCertificateMapper.insert(certificate) != 1) throw new BusinessException(409, "证书生成失败");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "证书编号或培训记录已存在，请刷新后重试");
        }
        return certificate;
    }

    private void validateOutcomeText(TrainingOutcomeSaveDTO dto) {
        for (String text : Arrays.asList(dto.getAcquiredSkills(), dto.getRehabEffect(), dto.getImprovement()))
            if (text != null && text.getBytes(StandardCharsets.UTF_8).length > 65535)
                throw new BusinessException(400, "成果文本UTF-8编码不能超过65535字节");
        if (dto.getRemark() != null && dto.getRemark().length() > 255)
            throw new BusinessException(400, "备注长度不能超过255");
        if (dto.getScore() != null && (dto.getScore().scale() > 2
                || dto.getScore().abs().compareTo(new java.math.BigDecimal("999.99")) > 0))
            throw new BusinessException(400, "成绩最多允许3位整数和2位小数");
    }

    private Set<String> skillTokens(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.split("[;；,，、\\r\\n]+")).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasOutcome(TrainingRecordPO record) {
        return record.getAssessmentDate() != null || StringUtils.hasText(record.getResult())
                || record.getScore() != null || StringUtils.hasText(record.getCertificateNo())
                || StringUtils.hasText(record.getAcquiredSkills()) || StringUtils.hasText(record.getRehabEffect())
                || StringUtils.hasText(record.getImprovement());
    }

    private void requireCancellationAllowed(TrainingRecordPO record, TrainingPlanPO plan) {
        if (hasOutcome(record)) throw new BusinessException(409, "已登记培训成果，不能取消报名或删除记录");
        if (!TrainingPlanState.validDates(plan) || !LocalDate.now().isBefore(plan.getStartDate()))
            throw new BusinessException(409, "培训开始后不能普通取消报名或删除历史记录");
    }

    private void rejectInlineOutcome(TrainingRecordSaveDTO dto, TrainingRecordPO existing) {
        if (dto.getScore() != null && (existing == null || existing.getScore() == null
                || dto.getScore().compareTo(existing.getScore()) != 0))
            throw new BusinessException(400, "请通过培训成果接口登记成绩");
        String certificate = normalizeText(dto.getCertificateNo());
        if (certificate != null && (existing == null || !certificate.equals(existing.getCertificateNo())))
            throw new BusinessException(400, "证书编号由培训成果接口自动生成");
    }

    private TrainingRecordPO requireRecord(Long id, boolean forUpdate) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) {
            throw new BusinessException(400, "培训记录 ID 必须大于 0");
        }
        TrainingRecordPO record = trainingRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(admin ? 404 : 403, RECORD_ACCESS_MESSAGE);
        }
        if (!admin) {
            requirePerson(record.getPersonId(), forUpdate, RECORD_ACCESS_MESSAGE);
        } else if (forUpdate) {
            // ADMIN 可维护历史记录；所属人员仍存在时锁定档案以保持写入顺序一致。
            disabledPersonMapper.selectAccessibleForUpdate(record.getPersonId(), null);
        }
        if (forUpdate) {
            TrainingRecordPO latest = trainingRecordMapper.selectForUpdate(id, record.getPersonId(), record.getPlanId());
            if (latest == null) {
                throw new BusinessException(admin ? 404 : 403, RECORD_ACCESS_MESSAGE);
            }
            return latest;
        }
        return record;
    }

    private void requirePerson(Long personId, boolean forUpdate, String deniedMessage) {
        if (personId == null || personId <= 0) {
            throw new BusinessException(400, "人员 ID 必须大于 0");
        }
        boolean admin = gridScopeService.isAdmin();
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        DisabledPersonPO person;
        if (forUpdate) {
            person = disabledPersonMapper.selectAccessibleForUpdate(personId, gridId);
        } else {
            LambdaQueryWrapper<DisabledPersonPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DisabledPersonPO::getId, personId);
            if (!admin) {
                wrapper.eq(DisabledPersonPO::getGridId, gridId);
            }
            person = disabledPersonMapper.selectOne(wrapper);
        }
        if (person == null || (!admin && !Objects.equals(gridId, person.getGridId()))) {
            throw new BusinessException(admin ? 404 : 403, deniedMessage);
        }
    }

    private TrainingPlanPO requirePlan(Long planId, boolean includeDeleted) {
        if (planId == null || planId <= 0) {
            throw new BusinessException(400, "培训计划 ID 必须大于 0");
        }
        // 报名资格按所属人员授权；不复用计划维护接口的创建人权限限制。
        TrainingPlanPO plan = trainingRecordMapper.selectPlanForUpdate(planId);
        if (plan == null || (!includeDeleted && !Integer.valueOf(0).equals(plan.getDeleted()))) {
            throw new BusinessException(404, "培训计划不存在或已删除");
        }
        return plan;
    }

    private void requireCapacity(TrainingPlanPO plan) {
        int enrolledCount = plan.getEnrolledCount() == null ? 0 : plan.getEnrolledCount();
        if (plan.getCapacity() == null || plan.getCapacity() <= 0 || enrolledCount < 0) {
            throw new BusinessException(409, "培训计划容量或报名人数配置异常");
        }
        if (enrolledCount >= plan.getCapacity()) {
            throw new BusinessException(409, "培训计划报名人数已满");
        }
    }

    /**
     * 报名资格校验：计划必须已发布且处于报名中，且仍有剩余名额。
     *
     * <p>新增和恢复均检查真实日期；取消由独立的开始前限制校验。
     */
    private void requireEnrollable(TrainingPlanPO plan) {
        if (!TrainingPlanService.PUBLISH_PUBLISHED.equals(plan.getPublishStatus())) {
            throw new BusinessException(409, "培训计划未发布，不能报名");
        }
        LocalDate today = LocalDate.now();
        if (!TrainingPlanState.validDates(plan) || !today.isBefore(plan.getStartDate())
                || !TrainingPlanService.STATUS_ENROLLING.equals(TrainingPlanState.effectiveStatus(plan, today))) {
            throw new BusinessException(409, "培训计划当前不接受报名");
        }
        requireCapacity(plan);
    }

    private void requireNoDuplicateEnrollment(Long personId, Long planId) {
        if (trainingRecordMapper.countEnrollment(personId, planId) > 0) {
            throw new BusinessException(409, DUPLICATE_ENROLLMENT_MESSAGE);
        }
    }

    private void increaseEnrollment(Long planId) {
        if (trainingRecordMapper.incrementEnrollment(planId, SecurityUtils.getUserId()) != 1) {
            throw new BusinessException(409, "培训计划容量已变更，请刷新后重试");
        }
    }

    private void decreaseEnrollment(Long planId) {
        if (trainingRecordMapper.decrementEnrollment(planId, SecurityUtils.getUserId()) != 1) {
            throw new BusinessException(409, "培训计划报名人数异常，无法恢复名额");
        }
    }

    private boolean isEffectiveEnrollment(String status) {
        return status != null && EFFECTIVE_STATUSES.contains(status);
    }

    private void validateStatus(String status) {
        if (status == null) {
            return;
        }
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, STATUS_DICT_TYPE)
                .eq(SysDictPO::getDictValue, status)
                .eq(SysDictPO::getStatus, 1)
                .select(SysDictPO::getDictValue);
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        if (dict == null || !status.equals(dict.getDictValue())) {
            throw new BusinessException(400, "培训记录状态字典值不存在或已停用");
        }
    }

    private void normalizeFields(TrainingRecordPO po) {
        po.setCertificateNo(normalizeText(po.getCertificateNo()));
        po.setRemark(normalizeText(po.getRemark()));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TrainingRecordVO toVO(TrainingRecordPO po, TrainingPlanPO plan, LocalDate today) {
        TrainingRecordVO vo = new TrainingRecordVO();
        BeanUtils.copyProperties(po, vo);
        vo.setStatus(TrainingRecordState.effectiveStatus(po.getStatus(), plan, today));
        return vo;
    }
}

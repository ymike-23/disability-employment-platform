package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.ResumeQueryDTO;
import com.yzr.disability.entity.dto.ResumeSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.ResumePO;
import com.yzr.disability.entity.vo.ResumeVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.ResumeMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.ResumeService;
import com.yzr.disability.service.TrainingRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final String ACCESS_MESSAGE = "简历不存在或无权访问";
    /** sys_dict.training_category 的就业类 dict_value。 */
    private static final String EMPLOYMENT_CATEGORY = "2";

    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private DisabledPersonMapper disabledPersonMapper;
    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public PageResult<ResumeVO> page(ResumeQueryDTO query) {
        boolean admin = gridScopeService.isAdmin();
        if (query == null) {
            throw new BusinessException(400, "查询条件不能为空");
        }
        if (query.getPersonId() != null) {
            validateId(query.getPersonId(), "人员");
        }
        validateText(query.getName(), 50, "姓名");
        long current = query.getCurrent() == null ? 1L : query.getCurrent();
        long size = query.getSize() == null ? 10L : query.getSize();
        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "页码必须大于0，每页条数必须在1至100之间");
        }
        Long gridId = null;
        if (!admin) {
            List<Long> gridIds = gridScopeService.getCurrentGridIds();
            if (gridIds.size() > 1) {
                throw new BusinessException(403, GridScopeService.MULTI_GRID_MESSAGE);
            }
            if (gridIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, current, size, 0);
            }
            gridId = gridIds.get(0);
            if (query.getPersonId() != null) {
                DisabledPersonPO person = disabledPersonMapper.selectById(query.getPersonId());
                if (person == null || !Objects.equals(gridId, person.getGridId())) {
                    throw new BusinessException(403, "档案不存在或无权访问");
                }
            }
        }
        return PageResult.of(resumeMapper.selectResumePage(new Page<>(current, size), query, gridId));
    }

    @Override
    public ResumeVO detail(Long id) {
        boolean admin = gridScopeService.isAdmin();
        validateId(id, "简历");
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        ResumeVO resume = resumeMapper.selectResumeDetail(id, gridId);
        if (resume == null) {
            throw new BusinessException(admin ? 404 : 403, ACCESS_MESSAGE);
        }
        return resume;
    }

    @Override
    // 不同人员首次建简历时，避免非唯一 person_id 索引的间隙锁互相阻塞插入。
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long create(ResumeSaveDTO dto) {
        validateSave(dto);
        requirePersonForWrite(dto.getPersonId());
        // shortcut: 唯一性依赖所有写入口先锁人员行，新增绕过本服务的写入口时需加条件唯一索引。
        // 查重使用当前读，避免等待人员锁前已建立的 MySQL 快照遗漏刚提交的简历。
        if (!resumeMapper.selectActiveIdsForUpdate(dto.getPersonId()).isEmpty()) {
            throw new BusinessException(409, "该人员已有当前简历，请修改已有简历");
        }
        ResumePO resume = new ResumePO();
        BeanUtils.copyProperties(dto, resume);
        resume.setSkillsSummary(mergeSkills(resume.getSkillsSummary(), resumeMapper.selectConfirmedEmploymentSkills(
                dto.getPersonId(), TrainingRecordService.RESULT_PASSED, EMPLOYMENT_CATEGORY)));
        validateTextBytes(resume.getSkillsSummary(), "技能摘要");
        LocalDateTime now = LocalDateTime.now();
        resume.setDeleted(0);
        resume.setCreateTime(now);
        resume.setUpdateTime(now);
        if (resumeMapper.insert(resume) != 1) {
            throw new BusinessException(409, "新增简历失败");
        }
        return resume.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ResumeSaveDTO dto) {
        ResumePO existing = requireResumeForWrite(id);
        validateSave(dto);
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId())) {
            throw new BusinessException(400, "不允许修改简历所属人员");
        }
        ResumePO changes = new ResumePO();
        BeanUtils.copyProperties(dto, changes);
        changes.setPersonId(null);
        if (changes.getSkillsSummary() != null) {
            changes.setSkillsSummary(mergeSkills(changes.getSkillsSummary(), resumeMapper.selectConfirmedEmploymentSkills(
                    existing.getPersonId(), TrainingRecordService.RESULT_PASSED, EMPLOYMENT_CATEGORY)));
            validateTextBytes(changes.getSkillsSummary(), "技能摘要");
        }
        changes.setUpdateTime(LocalDateTime.now());
        LambdaUpdateWrapper<ResumePO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ResumePO::getId, id).eq(ResumePO::getPersonId, existing.getPersonId());
        if (resumeMapper.update(changes, wrapper) != 1) {
            throw new BusinessException(409, "简历已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ResumePO existing = requireResumeForWrite(id);
        LambdaQueryWrapper<ResumePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResumePO::getId, id).eq(ResumePO::getPersonId, existing.getPersonId());
        if (resumeMapper.delete(wrapper) != 1) {
            throw new BusinessException(409, "简历已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeConfirmedTrainingSkills(Long personId, String acquiredSkills) {
        if (acquiredSkills == null || acquiredSkills.isBlank()) return;
        requirePersonForWrite(personId);
        List<Long> ids = resumeMapper.selectActiveIdsForUpdate(personId);
        if (ids.size() > 1) throw new BusinessException(409, "人员存在多份当前简历，请先核对历史数据");
        LocalDateTime now = LocalDateTime.now();
        if (ids.isEmpty()) {
            // 实库仅person_id必填；不伪造求职意向、学历、薪资或其他经历。
            ResumePO resume = new ResumePO();
            resume.setPersonId(personId);
            String confirmed = mergeSkills(acquiredSkills, resumeMapper.selectConfirmedEmploymentSkills(
                    personId, TrainingRecordService.RESULT_PASSED, EMPLOYMENT_CATEGORY));
            resume.setSkillsSummary(confirmed);
            validateTextBytes(resume.getSkillsSummary(), "技能摘要");
            resume.setDeleted(0);
            resume.setCreateTime(now);
            resume.setUpdateTime(now);
            if (resumeMapper.insert(resume) != 1) throw new BusinessException(409, "技能简历创建失败");
        } else {
            ResumePO existing = resumeMapper.selectForUpdate(ids.getFirst(), personId);
            if (existing == null) throw new BusinessException(409, "简历已变更，请刷新后重试");
            String summary = mergeSkills(existing.getSkillsSummary(), List.of(acquiredSkills));
            validateTextBytes(summary, "技能摘要");
            if (Objects.equals(summary, existing.getSkillsSummary())) return;
            ResumePO changes = new ResumePO();
            changes.setId(existing.getId());
            changes.setSkillsSummary(summary);
            changes.setUpdateTime(now);
            if (resumeMapper.updateById(changes) != 1) throw new BusinessException(409, "技能摘要更新失败");
        }
    }

    private String mergeSkills(String existing, Collection<String> additions) {
        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        java.util.stream.Stream.concat(java.util.stream.Stream.of(existing), additions.stream())
                .filter(Objects::nonNull).flatMap(value -> java.util.Arrays.stream(value.split("[;；,，、\\r\\n]+")))
                .map(String::trim).filter(value -> !value.isBlank())
                .forEach(value -> skills.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        return String.join("；", skills.values());
    }

    private ResumePO requireResumeForWrite(Long id) {
        boolean admin = gridScopeService.isAdmin();
        validateId(id, "简历");
        ResumePO observed = resumeMapper.selectById(id);
        if (observed == null) {
            throw new BusinessException(admin ? 404 : 403, ACCESS_MESSAGE);
        }
        requirePersonForWrite(observed.getPersonId());
        ResumePO latest = resumeMapper.selectForUpdate(id, observed.getPersonId());
        if (latest == null) {
            throw new BusinessException(admin ? 404 : 403, ACCESS_MESSAGE);
        }
        return latest;
    }

    private void requirePersonForWrite(Long personId) {
        boolean admin = gridScopeService.isAdmin();
        validateId(personId, "人员");
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        if (disabledPersonMapper.selectAccessibleForUpdate(personId, gridId) == null) {
            throw new BusinessException(admin ? 404 : 403, "档案不存在或无权访问");
        }
    }

    private void validateSave(ResumeSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "简历参数不能为空");
        }
        validateId(dto.getPersonId(), "人员");
        validateText(dto.getJobIntention(), 255, "求职意向");
        validateText(dto.getExpectedSalary(), 50, "期望薪资");
        validateTextBytes(dto.getSelfEvaluation(), "自我评价");
        validateTextBytes(dto.getSkillsSummary(), "技能摘要");
    }

    private void validateId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, name + " ID 必须大于 0");
        }
    }

    private void validateText(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400, name + "长度不能超过" + maxLength);
        }
    }

    private void validateTextBytes(String value, String name) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException(400, name + "UTF-8编码不能超过65535字节");
        }
    }
}

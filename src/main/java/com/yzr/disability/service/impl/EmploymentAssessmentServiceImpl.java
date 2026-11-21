package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.EmploymentAssessmentQueryDTO;
import com.yzr.disability.entity.dto.EmploymentAssessmentSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.EmploymentAssessmentPO;
import com.yzr.disability.entity.vo.EmploymentAssessmentVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.EmploymentAssessmentMapper;
import com.yzr.disability.service.EmploymentAssessmentService;
import com.yzr.disability.service.GridScopeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class EmploymentAssessmentServiceImpl implements EmploymentAssessmentService {
    @Autowired private EmploymentAssessmentMapper employmentAssessmentMapper;
    @Autowired private DisabledPersonMapper disabledPersonMapper;
    @Autowired private GridScopeService gridScopeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(EmploymentAssessmentSaveDTO dto) {
        validateSave(dto);
        requirePerson(dto.getPersonId(), true);
        EmploymentAssessmentPO po = new EmploymentAssessmentPO();
        BeanUtils.copyProperties(dto, po);
        if (po.getEmploymentIntention() == null) po.setEmploymentIntention("3");
        if (po.getAssessmentDate() == null) po.setAssessmentDate(LocalDate.now());
        po.setOperatorId(operator());
        po.setDeleted(0);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(po.getCreateTime());
        if (employmentAssessmentMapper.insert(po) != 1) throw new BusinessException(409, "新增评估失败");
        return po.getId();
    }

    @Override
    public PageResult<EmploymentAssessmentVO> page(EmploymentAssessmentQueryDTO query) {
        boolean admin = gridScopeService.isAdmin();
        if (query == null) throw new BusinessException(400, "查询条件不能为空");
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        if (current < 1 || size < 1 || size > 100) throw new BusinessException(400, "分页参数无效");
        validateIntention(query.getEmploymentIntention());
        if (query.getDateFrom() != null && query.getDateTo() != null
                && query.getDateFrom().isAfter(query.getDateTo()))
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        if (query.getPersonId() != null) requirePerson(query.getPersonId(), false);
        LambdaQueryWrapper<EmploymentAssessmentPO> wrapper = new LambdaQueryWrapper<>();
        if (!admin) {
            List<Long> grids = gridScopeService.getCurrentGridIds();
            if (grids.size() > 1) throw new BusinessException(403, GridScopeService.MULTI_GRID_MESSAGE);
            if (grids.isEmpty()) return new PageResult<>(List.of(), 0, current, size, 0);
            wrapper.exists("SELECT 1 FROM disabled_person p WHERE p.id = employment_assessment.person_id"
                    + " AND p.deleted = 0 AND p.grid_id = {0}", grids.getFirst());
        } else {
            wrapper.exists("SELECT 1 FROM disabled_person p WHERE p.id = employment_assessment.person_id AND p.deleted = 0");
        }
        if (query.getPersonId() != null) wrapper.eq(EmploymentAssessmentPO::getPersonId, query.getPersonId());
        if (query.getEmploymentIntention() != null)
            wrapper.eq(EmploymentAssessmentPO::getEmploymentIntention, query.getEmploymentIntention());
        if (query.getDateFrom() != null) wrapper.ge(EmploymentAssessmentPO::getAssessmentDate, query.getDateFrom());
        if (query.getDateTo() != null) wrapper.le(EmploymentAssessmentPO::getAssessmentDate, query.getDateTo());
        wrapper.orderByDesc(EmploymentAssessmentPO::getAssessmentDate).orderByDesc(EmploymentAssessmentPO::getId);
        Page<EmploymentAssessmentPO> result = employmentAssessmentMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public EmploymentAssessmentVO detail(Long id) {
        return toVO(requireRecord(id, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EmploymentAssessmentSaveDTO dto) {
        EmploymentAssessmentPO existing = requireRecord(id, true);
        validateSave(dto);
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId()))
            throw new BusinessException(400, "不允许修改评估所属人员");
        EmploymentAssessmentPO changes = new EmploymentAssessmentPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setPersonId(null);
        changes.setId(id);
        changes.setOperatorId(operator());
        changes.setUpdateTime(LocalDateTime.now());
        if (employmentAssessmentMapper.updateById(changes) != 1)
            throw new BusinessException(409, "评估已变更，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EmploymentAssessmentPO existing = requireRecord(id, true);
        if (employmentAssessmentMapper.deleteById(existing.getId()) != 1)
            throw new BusinessException(409, "评估已变更，请刷新后重试");
    }

    private EmploymentAssessmentPO requireRecord(Long id, boolean forUpdate) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) throw new BusinessException(400, "评估ID必须大于0");
        EmploymentAssessmentPO po = employmentAssessmentMapper.selectById(id);
        if (po == null) throw new BusinessException(admin ? 404 : 403, "评估不存在或无权访问");
        requirePerson(po.getPersonId(), forUpdate);
        if (forUpdate) {
            po = employmentAssessmentMapper.selectForUpdate(id, po.getPersonId());
            if (po == null) throw new BusinessException(409, "评估已变更，请刷新后重试");
        }
        return po;
    }

    private void requirePerson(Long personId, boolean forUpdate) {
        boolean admin = gridScopeService.isAdmin();
        if (personId == null || personId <= 0) throw new BusinessException(400, "人员ID必须大于0");
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        DisabledPersonPO person = forUpdate ? disabledPersonMapper.selectAccessibleForUpdate(personId, gridId)
                : disabledPersonMapper.selectById(personId);
        if (person == null || (!admin && !Objects.equals(gridId, person.getGridId())))
            throw new BusinessException(admin ? 404 : 403, "人员不存在或无权访问");
    }

    private void validateSave(EmploymentAssessmentSaveDTO dto) {
        if (dto == null || dto.getPersonId() == null || dto.getPersonId() <= 0)
            throw new BusinessException(400, "人员ID必须大于0");
        validateIntention(dto.getEmploymentIntention());
        if (dto.getTrainingSuggestion() != null && dto.getTrainingSuggestion().length() > 100)
            throw new BusinessException(400, "培训建议长度不能超过100");
        for (String value : Arrays.asList(dto.getExistingSkills(), dto.getBasicAbility(), dto.getSupportNeeds(),
                dto.getDevelopmentDirection(), dto.getAssessmentSummary()))
            if (value != null && value.getBytes(StandardCharsets.UTF_8).length > 65535)
                throw new BusinessException(400, "评估文本UTF-8编码不能超过65535字节");
    }

    private void validateIntention(String value) {
        if (value != null && !Set.of("1", "2", "3").contains(value))
            throw new BusinessException(400, "就业意愿必须为1、2或3");
    }

    private Long operator() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new BusinessException(403, "无法获取当前操作人");
        return id;
    }

    private EmploymentAssessmentVO toVO(EmploymentAssessmentPO po) {
        EmploymentAssessmentVO vo = new EmploymentAssessmentVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }
}


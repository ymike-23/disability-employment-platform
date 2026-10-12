package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.dto.TrainingPlanSaveDTO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.vo.TrainingPlanVO;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 培训计划管理；通过创建人的当前有效网格限定 BUSINESS 数据范围。 */
@Service
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String PLAN_ACCESS_MESSAGE = "培训计划不存在或无权访问";

    @Autowired
    private TrainingPlanMapper trainingPlanMapper;

    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public PageResult<TrainingPlanVO> page(TrainingPlanQueryDTO query) {
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
        }
        validateStatus(query.getStatus());
        validateCategory(query.getCategory());
        LambdaQueryWrapper<TrainingPlanPO> wrapper = new LambdaQueryWrapper<>();
        applyGridScope(wrapper, gridId);
        if (StringUtils.hasText(query.getPlanName())) {
            wrapper.like(TrainingPlanPO::getPlanName, query.getPlanName().trim());
        }
        if (query.getCategory() != null) {
            wrapper.eq(TrainingPlanPO::getCategory, query.getCategory());
        }
        if (StringUtils.hasText(query.getTrainingType())) {
            wrapper.eq(TrainingPlanPO::getTrainingType, query.getTrainingType().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(TrainingPlanPO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(TrainingPlanPO::getCreateTime).orderByDesc(TrainingPlanPO::getId);

        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getSize(), MAX_PAGE_SIZE);
        Page<TrainingPlanPO> result = trainingPlanMapper.selectPage(new Page<>(current, size), wrapper);
        List<TrainingPlanVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public TrainingPlanVO detail(Long id) {
        return toVO(requirePlan(id, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TrainingPlanSaveDTO dto) {
        if (!gridScopeService.isAdmin()) {
            gridScopeService.requireSingleGridId();
        }
        int enrolledCount = dto.getEnrolledCount() == null ? 0 : dto.getEnrolledCount();
        validatePlan(dto, enrolledCount);
        TrainingPlanPO po = new TrainingPlanPO();
        BeanUtils.copyProperties(dto, po);
        normalizeFields(po);
        po.setEnrolledCount(enrolledCount);
        po.setStatus(dto.getStatus() == null ? STATUS_NOT_STARTED : dto.getStatus());
        po.setDeleted(0);
        // 仅使用当前登录账号作为创建人，不接受前端伪造的人员、网格或创建人。
        po.setCreateBy(SecurityUtils.getUserId());
        LocalDateTime now = LocalDateTime.now();
        po.setCreateTime(now);
        po.setUpdateTime(now);
        if (trainingPlanMapper.insert(po) != 1) {
            throw new BusinessException(409, "新增培训计划失败");
        }
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TrainingPlanSaveDTO dto) {
        TrainingPlanPO existing = requirePlan(id, true);
        int currentEnrolledCount = existing.getEnrolledCount() == null ? 0 : existing.getEnrolledCount();
        int enrolledCount = dto.getEnrolledCount() == null ? currentEnrolledCount : dto.getEnrolledCount();
        validatePlan(dto, enrolledCount);
        if (currentEnrolledCount < 0) {
            throw new BusinessException(400, "当前已报名人数不能小于 0");
        }
        // 即使请求同时降低报名人数，也不能绕过对数据库当前报名人数的容量约束。
        if (dto.getCapacity() < currentEnrolledCount) {
            throw new BusinessException(400, "计划人数不能小于当前已报名人数");
        }
        TrainingPlanPO changes = new TrainingPlanPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setId(id);
        normalizeFields(changes);
        changes.setUpdateBy(SecurityUtils.getUserId());
        changes.setUpdateTime(LocalDateTime.now());
        if (trainingPlanMapper.updateById(changes) != 1) {
            throw new BusinessException(409, "培训计划已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TrainingPlanPO existing = requirePlan(id, true);
        // @TableLogic 转换为 deleted=1，保留计划记录。
        if (trainingPlanMapper.deleteById(existing.getId()) != 1) {
            throw new BusinessException(409, "培训计划已变更，请刷新后重试");
        }
    }

    private TrainingPlanPO requirePlan(Long id, boolean forUpdate) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) {
            throw new BusinessException(400, "培训计划 ID 必须大于 0");
        }
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        TrainingPlanPO plan;
        if (forUpdate) {
            plan = trainingPlanMapper.selectAccessibleForUpdate(id, gridId);
        } else {
            LambdaQueryWrapper<TrainingPlanPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TrainingPlanPO::getId, id);
            applyGridScope(wrapper, gridId);
            plan = trainingPlanMapper.selectOne(wrapper);
        }
        if (plan == null) {
            throw new BusinessException(admin ? 404 : 403, PLAN_ACCESS_MESSAGE);
        }
        return plan;
    }

    private void applyGridScope(LambdaQueryWrapper<TrainingPlanPO> wrapper, Long gridId) {
        if (gridId != null) {
            // 培训计划没有 grid_id/person_id；按真实 create_by 关联既有网格配置。
            // 数据范围在分页、计数、详情查询前生效，不采用请求中的 personId。
            wrapper.exists("SELECT 1 FROM sys_grid g"
                    + " WHERE g.id = {0} AND g.grid_worker_id = training_plan.create_by"
                    + " AND g.status = 1 AND g.deleted = 0", gridId);
        }
    }

    private void validatePlan(TrainingPlanSaveDTO dto, int enrolledCount) {
        if (!StringUtils.hasText(dto.getPlanName())) {
            throw new BusinessException(400, "计划名称不能为空");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new BusinessException(400, "开始日期和结束日期不能为空");
        }
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
        if (dto.getCapacity() == null || dto.getCapacity() <= 0) {
            throw new BusinessException(400, "计划人数必须大于 0");
        }
        if (enrolledCount < 0) {
            throw new BusinessException(400, "已报名人数不能小于 0");
        }
        if (enrolledCount > dto.getCapacity()) {
            throw new BusinessException(400, "已报名人数不能超过计划人数");
        }
        validateStatus(dto.getStatus());
        validateCategory(dto.getCategory());
    }

    private void validateStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BusinessException(400, "计划状态只能为 0（未开始）、1（进行中）、2（已结束）");
        }
    }

    private void validateCategory(String category) {
        if (category != null && !"1".equals(category) && !"2".equals(category)) {
            throw new BusinessException(400, "培训大类只能为 1（康复类）、2（就业类）");
        }
    }

    private void normalizeFields(TrainingPlanPO po) {
        po.setPlanName(normalizeText(po.getPlanName()));
        po.setTrainingType(normalizeText(po.getTrainingType()));
        po.setLocation(normalizeText(po.getLocation()));
        po.setOrganizer(normalizeText(po.getOrganizer()));
        po.setRemark(normalizeText(po.getRemark()));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TrainingPlanVO toVO(TrainingPlanPO po) {
        TrainingPlanVO vo = new TrainingPlanVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }
}

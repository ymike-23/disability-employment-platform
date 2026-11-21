package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.common.util.TrainingPlanState;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.dto.TrainingPlanSaveDTO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.vo.TrainingPlanVO;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.mapper.TrainingPlanMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingPlanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 培训计划管理。
 *
 * <p>计划由管理员统一维护：管理员可新增、修改、删除并发布计划，可查看全部计划
 * （含草稿）；业务人员只能查看全平台已发布的计划，不再按 create_by 或 grid_id 过滤。
 * 报名人数只能由培训记录维护，不能通过计划接口直接写入。
 */
@Service
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String PLAN_ACCESS_MESSAGE = "培训计划不存在或无权访问";
    private static final String CATEGORY_DICT_TYPE = "training_category";
    private static final String TRAINING_DICT_TYPE = "training_type";
    private static final String STATUS_DICT_TYPE = "training_status";
    private static final String PUBLISH_DICT_TYPE = "training_publish_status";

    @Autowired
    private TrainingPlanMapper trainingPlanMapper;

    @Autowired
    private SysDictMapper sysDictMapper;

    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public PageResult<TrainingPlanVO> page(TrainingPlanQueryDTO query) {
        LocalDate today = LocalDate.now();
        boolean admin = gridScopeService.isAdmin();
        validateDictValue(STATUS_DICT_TYPE, query.getStatus(), "计划状态");
        validateDictValue(CATEGORY_DICT_TYPE, query.getCategory(), "培训大类");
        validateDictValue(TRAINING_DICT_TYPE, query.getTrainingType(), "培训类型");
        validateDictValue(PUBLISH_DICT_TYPE, query.getPublishStatus(), "发布状态");

        LambdaQueryWrapper<TrainingPlanPO> wrapper = new LambdaQueryWrapper<>();
        if (admin) {
            // 管理员查看全部计划，包含草稿；不按创建人或网格过滤。
            if (StringUtils.hasText(query.getPublishStatus())) {
                wrapper.eq(TrainingPlanPO::getPublishStatus, query.getPublishStatus().trim());
            }
        } else {
            // 业务人员只能查看全平台已发布计划，草稿筛选条件一律忽略。
            wrapper.eq(TrainingPlanPO::getPublishStatus, PUBLISH_PUBLISHED);
        }
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
            wrapper.apply("(" + TrainingPlanState.STATUS_SQL + ") = {1}", today, query.getStatus());
        }
        wrapper.orderByDesc(TrainingPlanPO::getCreateTime).orderByDesc(TrainingPlanPO::getId);

        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getSize(), MAX_PAGE_SIZE);
        Page<TrainingPlanPO> result = trainingPlanMapper.selectPage(new Page<>(current, size), wrapper);
        List<TrainingPlanVO> records = result.getRecords().stream().map(plan -> toVO(plan, today)).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public TrainingPlanVO detail(Long id) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) {
            throw new BusinessException(400, "培训计划 ID 必须大于 0");
        }
        TrainingPlanPO plan = trainingPlanMapper.selectById(id);
        // 业务人员不能查看未发布计划，返回 403 且不泄露存在性。
        if (plan == null || (!admin && !PUBLISH_PUBLISHED.equals(plan.getPublishStatus()))) {
            throw new BusinessException(admin ? 404 : 403, PLAN_ACCESS_MESSAGE);
        }
        return toVO(plan, LocalDate.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TrainingPlanSaveDTO dto) {
        validatePlan(dto);
        String status = dto.getStatus() == null ? STATUS_NOT_STARTED : dto.getStatus();
        // 默认状态同样必须对应启用的字典项。
        validateDictValue(STATUS_DICT_TYPE, status, "计划状态");
        TrainingPlanPO po = new TrainingPlanPO();
        BeanUtils.copyProperties(dto, po);
        normalizeFields(po);
        // 报名人数只能由培训记录维护，新增计划固定为 0。
        po.setEnrolledCount(0);
        po.setStatus(status);
        // 普通保存不能绕过发布流程，新增计划统一为草稿。
        po.setPublishStatus(PUBLISH_DRAFT);
        po.setDeleted(0);
        // 仅使用当前登录账号作为创建人，不接受前端伪造的创建人。
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
        validatePlan(dto);
        validateDictValue(STATUS_DICT_TYPE, dto.getStatus(), "计划状态");
        int currentEnrolledCount = existing.getEnrolledCount() == null ? 0 : existing.getEnrolledCount();
        if (currentEnrolledCount < 0) {
            throw new BusinessException(400, "当前已报名人数不能小于 0");
        }
        // 即使请求同时降低容量，也不能绕过对数据库当前报名人数的容量约束。
        if (dto.getCapacity() < currentEnrolledCount) {
            throw new BusinessException(400, "计划人数不能小于当前已报名人数");
        }
        TrainingPlanPO changes = new TrainingPlanPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setId(id);
        normalizeFields(changes);
        // 报名人数与发布状态只能由培训记录、发布接口维护，普通修改一律忽略。
        changes.setEnrolledCount(null);
        changes.setPublishStatus(null);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        TrainingPlanPO existing = requirePlan(id, true);
        if (PUBLISH_PUBLISHED.equals(existing.getPublishStatus())) {
            throw new BusinessException(409, "培训计划已发布");
        }
        if (STATUS_CANCELLED.equals(existing.getStatus())) {
            throw new BusinessException(409, "已取消的培训计划不能发布");
        }
        if (existing.getCapacity() == null || existing.getCapacity() <= 0) {
            throw new BusinessException(409, "培训计划容量配置异常，不能发布");
        }
        // 目标发布状态同样必须是启用的字典项。
        validateDictValue(PUBLISH_DICT_TYPE, PUBLISH_PUBLISHED, "发布状态");
        TrainingPlanPO changes = new TrainingPlanPO();
        changes.setId(id);
        changes.setPublishStatus(PUBLISH_PUBLISHED);
        changes.setUpdateBy(SecurityUtils.getUserId());
        changes.setUpdateTime(LocalDateTime.now());
        if (trainingPlanMapper.updateById(changes) != 1) {
            throw new BusinessException(409, "培训计划已变更，请刷新后重试");
        }
    }

    /**
     * 锁定并校验计划可维护性；计划权限不再与网格或创建人绑定，改由方法级角色控制。
     */
    private TrainingPlanPO requirePlan(Long id, boolean forUpdate) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "培训计划 ID 必须大于 0");
        }
        // 触发角色准入校验，防止无 ADMIN/BUSINESS 角色的账号进入。
        gridScopeService.isAdmin();
        TrainingPlanPO plan = forUpdate
                ? trainingPlanMapper.selectAccessibleForUpdate(id)
                : trainingPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(404, PLAN_ACCESS_MESSAGE);
        }
        return plan;
    }

    private void validatePlan(TrainingPlanSaveDTO dto) {
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
        validateDictValue(CATEGORY_DICT_TYPE, dto.getCategory(), "培训大类");
        validateDictValue(TRAINING_DICT_TYPE, dto.getTrainingType(), "培训类型");
    }

    private void validateDictValue(String dictType, String value, String fieldName) {
        if (value == null) {
            return;
        }
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, dictType)
                .eq(SysDictPO::getDictValue, value)
                .eq(SysDictPO::getStatus, 1)
                .select(SysDictPO::getDictValue);
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        if (dict == null || !value.equals(dict.getDictValue())) {
            throw new BusinessException(400, fieldName + "字典值不存在或已停用");
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

    private TrainingPlanVO toVO(TrainingPlanPO po, LocalDate today) {
        TrainingPlanVO vo = new TrainingPlanVO();
        BeanUtils.copyProperties(po, vo);
        vo.setStatus(TrainingPlanState.effectiveStatus(po, today));
        return vo;
    }
}

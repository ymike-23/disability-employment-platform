package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.TrainingDemandQueryDTO;
import com.yzr.disability.entity.dto.TrainingDemandSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.TrainingDemandPO;
import com.yzr.disability.entity.vo.TrainingDemandVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.TrainingDemandMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingDemandService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TrainingDemandServiceImpl implements TrainingDemandService {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String DEMAND_ACCESS_MESSAGE = "培训需求不存在或无权访问";

    @Autowired
    private TrainingDemandMapper trainingDemandMapper;

    @Autowired
    private DisabledPersonMapper disabledPersonMapper;

    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public PageResult<TrainingDemandVO> page(TrainingDemandQueryDTO query) {
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
        if (query.getPersonId() != null) {
            requirePerson(query.getPersonId(), false, "档案不存在或无权访问");
        }
        validateStatus(query.getStatus());

        LambdaQueryWrapper<TrainingDemandPO> wrapper = new LambdaQueryWrapper<>();
        if (admin) {
            wrapper.exists("SELECT 1 FROM disabled_person p"
                    + " WHERE p.id = training_demand.person_id AND p.deleted = 0");
        } else {
            // 在分页和计数之前限定所属人员网格，gridId 使用绑定参数。
            wrapper.exists("SELECT 1 FROM disabled_person p"
                    + " WHERE p.id = training_demand.person_id AND p.deleted = 0 AND p.grid_id = {0}", gridId);
        }
        if (query.getPersonId() != null) {
            wrapper.eq(TrainingDemandPO::getPersonId, query.getPersonId());
        }
        if (StringUtils.hasText(query.getDemandType())) {
            wrapper.eq(TrainingDemandPO::getDemandType, query.getDemandType().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(TrainingDemandPO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(TrainingDemandPO::getCreateTime).orderByDesc(TrainingDemandPO::getId);

        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getSize(), MAX_PAGE_SIZE);
        Page<TrainingDemandPO> result = trainingDemandMapper.selectPage(new Page<>(current, size), wrapper);
        List<TrainingDemandVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public TrainingDemandVO detail(Long id) {
        return toVO(requireDemand(id, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TrainingDemandSaveDTO dto) {
        // 复用已有的人员权限锁查询，避免人员删除或归属变化与需求写入交错。
        requirePerson(dto.getPersonId(), true, "档案不存在或无权访问");
        validateStatus(dto.getStatus());
        TrainingDemandPO po = new TrainingDemandPO();
        BeanUtils.copyProperties(dto, po);
        normalizeFields(po);
        po.setStatus(dto.getStatus() == null ? STATUS_PENDING : dto.getStatus());
        po.setDeleted(0);
        po.setCreateBy(SecurityUtils.getUserId());
        LocalDateTime now = LocalDateTime.now();
        po.setCreateTime(now);
        po.setUpdateTime(now);
        if (trainingDemandMapper.insert(po) != 1) {
            throw new BusinessException(409, "新增培训需求失败");
        }
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TrainingDemandSaveDTO dto) {
        TrainingDemandPO existing = requireDemand(id, true);
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId())) {
            throw new BusinessException(400, "不允许修改培训需求所属人员");
        }
        validateStatus(dto.getStatus());
        TrainingDemandPO changes = new TrainingDemandPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setPersonId(null);
        normalizeFields(changes);
        changes.setUpdateBy(SecurityUtils.getUserId());
        changes.setUpdateTime(LocalDateTime.now());

        LambdaUpdateWrapper<TrainingDemandPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TrainingDemandPO::getId, id)
                .eq(TrainingDemandPO::getPersonId, existing.getPersonId());
        if (trainingDemandMapper.update(changes, wrapper) != 1) {
            throw new BusinessException(409, "培训需求已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TrainingDemandPO existing = requireDemand(id, true);
        LambdaQueryWrapper<TrainingDemandPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingDemandPO::getId, id)
                .eq(TrainingDemandPO::getPersonId, existing.getPersonId());
        // @TableLogic 转换为 deleted=1，保留需求记录。
        if (trainingDemandMapper.delete(wrapper) != 1) {
            throw new BusinessException(409, "培训需求已变更，请刷新后重试");
        }
    }

    private TrainingDemandPO requireDemand(Long id, boolean forUpdate) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) {
            throw new BusinessException(400, "培训需求 ID 必须大于 0");
        }
        TrainingDemandPO demand = trainingDemandMapper.selectById(id);
        if (demand == null) {
            throw new BusinessException(admin ? 404 : 403, DEMAND_ACCESS_MESSAGE);
        }
        // 用真实记录的人员 ID 校验权限，不采用前端传入的 personId。
        requirePerson(demand.getPersonId(), forUpdate, DEMAND_ACCESS_MESSAGE);
        if (forUpdate) {
            TrainingDemandPO latest = trainingDemandMapper.selectById(id);
            if (latest == null || !Objects.equals(latest.getPersonId(), demand.getPersonId())) {
                throw new BusinessException(admin ? 404 : 403, DEMAND_ACCESS_MESSAGE);
            }
            return latest;
        }
        return demand;
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

    private void validateStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BusinessException(400, "需求状态只能为 0（待处理）、1（已安排）、2（已取消）");
        }
    }

    private void normalizeFields(TrainingDemandPO po) {
        po.setDemandType(normalizeText(po.getDemandType()));
        po.setDemandDesc(normalizeText(po.getDemandDesc()));
        po.setRemark(normalizeText(po.getRemark()));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TrainingDemandVO toVO(TrainingDemandPO po) {
        TrainingDemandVO vo = new TrainingDemandVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }
}

package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.DisabledPersonQueryDTO;
import com.yzr.disability.entity.dto.DisabledPersonSaveDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.vo.DisabledPersonVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.service.DisabledPersonService;
import com.yzr.disability.service.GridScopeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DisabledPersonServiceImpl implements DisabledPersonService {

    /** 每页最大条数，避免无限制查询 */
    private static final long MAX_PAGE_SIZE = 100L;

    private static final long DEFAULT_PAGE_SIZE = 10L;

    @Autowired
    private DisabledPersonMapper disabledPersonMapper;

    @Autowired
    private SysGridMapper sysGridMapper;

    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public PageResult<DisabledPersonVO> page(DisabledPersonQueryDTO query) {
        long current = normalizeCurrent(query.getCurrent());
        long size = normalizeSize(query.getSize());

        Page<DisabledPersonPO> page = new Page<>(current, size);
        LambdaQueryWrapper<DisabledPersonPO> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getName())) {
            wrapper.like(DisabledPersonPO::getName, query.getName().trim());
        }
        if (StringUtils.hasText(query.getDistrict())) {
            wrapper.eq(DisabledPersonPO::getDistrict, query.getDistrict().trim());
        }
        if (StringUtils.hasText(query.getDisabilityType())) {
            wrapper.eq(DisabledPersonPO::getDisabilityType, query.getDisabilityType().trim());
        }

        if (!gridScopeService.isAdmin()) {
            List<Long> gridIds = gridScopeService.getCurrentGridIds();
            if (gridIds.size() > 1) {
                // 配置异常：拒绝访问，绝不扩大权限
                throw new BusinessException(GridScopeService.MULTI_GRID_MESSAGE);
            }
            if (gridIds.isEmpty()) {
                // 无网格：返回空分页（不降级为全量）
                return PageResult.empty();
            }
            wrapper.eq(DisabledPersonPO::getGridId, gridIds.get(0));
        } else if (query.getGridId() != null) {
            // 管理员可按网格筛选
            wrapper.eq(DisabledPersonPO::getGridId, query.getGridId());
        }

        Page<DisabledPersonPO> result = disabledPersonMapper.selectPage(page, wrapper);
        Map<Long, String> gridNameMap = loadGridNames(result.getRecords());

        List<DisabledPersonVO> voList = result.getRecords().stream()
                .map(po -> toVO(po, gridNameMap))
                .toList();

        return new PageResult<>(
                voList,
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    @Override
    public DisabledPersonVO detail(Long id) {
        DisabledPersonPO po = disabledPersonMapper.selectById(id);

        if (gridScopeService.isAdmin()) {
            // 管理员：可明确区分"档案不存在"
            if (po == null) {
                throw new BusinessException(404, "档案不存在");
            }
        } else {
            // 业务人员：不区分"不存在"与"越权"，统一 403
            // 避免通过 404/403 差异枚举出其他网格档案的存在性
            Long gridId = gridScopeService.requireSingleGridId();
            if (po == null || !Objects.equals(gridId, po.getGridId())) {
                throw new BusinessException(403, "档案不存在或无权访问");
            }
        }

        Map<Long, String> gridNameMap = loadGridNames(List.of(po));
        return toVO(po, gridNameMap);
    }

    @Override
    public Long create(DisabledPersonSaveDTO dto) {
        // 身份证号查重（排除软删除记录；数据库无唯一索引，存在并发风险）
        checkIdCardDuplicate(dto.getIdCard(), null);

        Long gridId;
        if (gridScopeService.isAdmin()) {
            gridId = dto.getGridId();
            if (gridId != null) {
                validateGridUsable(gridId);
            }
        } else {
            // 业务人员：忽略前端传值，由后端解析
            gridId = gridScopeService.requireSingleGridId();
        }

        DisabledPersonPO po = new DisabledPersonPO();
        BeanUtils.copyProperties(dto, po);
        po.setId(null);
        po.setGridId(gridId);
        // status 使用数据库默认值语义，由后端统一置为 1
        po.setStatus(1);
        po.setCreateBy(SecurityUtils.getUserId());

        disabledPersonMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Long id, DisabledPersonSaveDTO dto) {
        // 身份证号查重（排除当前记录）
        checkIdCardDuplicate(dto.getIdCard(), id);

        LambdaUpdateWrapper<DisabledPersonPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DisabledPersonPO::getId, id);

        if (!gridScopeService.isAdmin()) {
            // 关键：数据权限写入 WHERE 条件，防止越权修改
            wrapper.eq(DisabledPersonPO::getGridId, gridScopeService.requireSingleGridId());
        }

        DisabledPersonPO po = new DisabledPersonPO();
        BeanUtils.copyProperties(dto, po);
        po.setId(null);
        po.setGridId(null);
        po.setStatus(null);
        po.setDeleted(null);
        po.setUpdateBy(SecurityUtils.getUserId());

        int rows = disabledPersonMapper.update(po, wrapper);
        if (rows == 0) {
            throw new BusinessException(403, "档案不存在或无权修改");
        }
    }

    @Override
    public void delete(Long id) {
        LambdaQueryWrapper<DisabledPersonPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledPersonPO::getId, id);

        if (!gridScopeService.isAdmin()) {
            // 关键：数据权限写入 WHERE 条件，防止越权删除
            wrapper.eq(DisabledPersonPO::getGridId, gridScopeService.requireSingleGridId());
        }

        // @TableLogic 自动转换为逻辑删除 UPDATE
        int rows = disabledPersonMapper.delete(wrapper);
        if (rows == 0) {
            throw new BusinessException(403, "档案不存在或无权删除");
        }
    }

    /**
     * 身份证号查重。
     *
     * <p>注意：数据库 id_card 无唯一索引，此校验为应用层"先查后写"，
     * 并发场景下仍可能写入重复数据（TOCTOU），根治需添加数据库唯一索引。
     */
    private void checkIdCardDuplicate(String idCard, Long excludeId) {
        if (!StringUtils.hasText(idCard)) {
            return;
        }
        LambdaQueryWrapper<DisabledPersonPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledPersonPO::getIdCard, idCard.trim());
        if (excludeId != null) {
            wrapper.ne(DisabledPersonPO::getId, excludeId);
        }
        // deleted 由 @TableLogic 自动过滤（排除软删除）
        if (disabledPersonMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("身份证号已存在");
        }
    }

    /**
     * 校验网格存在、未软删除且状态有效（管理员指定网格时使用）
     */
    private void validateGridUsable(Long gridId) {
        SysGridPO grid = sysGridMapper.selectById(gridId);
        if (grid == null || grid.getStatus() == null || grid.getStatus() != 1) {
            throw new BusinessException("指定的网格不存在或已停用");
        }
    }

    private Map<Long, String> loadGridNames(List<DisabledPersonPO> records) {
        Set<Long> gridIds = records.stream()
                .map(DisabledPersonPO::getGridId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (gridIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysGridMapper.selectBatchIds(gridIds).stream()
                .filter(g -> g.getId() != null && g.getGridName() != null)
                .collect(Collectors.toMap(SysGridPO::getId, SysGridPO::getGridName, (a, b) -> a));
    }

    private DisabledPersonVO toVO(DisabledPersonPO po, Map<Long, String> gridNameMap) {
        DisabledPersonVO vo = new DisabledPersonVO();
        BeanUtils.copyProperties(po, vo);
        if (po.getGridId() != null) {
            vo.setGridName(gridNameMap.get(po.getGridId()));
        }
        return vo;
    }

    private long normalizeCurrent(Long current) {
        return current == null || current < 1 ? 1L : current;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}

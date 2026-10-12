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

import java.time.LocalDate;
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

        // 证件筛选条件：叠加在数据权限条件之上，不会放宽权限
        applyCertFilters(wrapper, query);

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
        // 证件信息校验与残疾人证号查重
        validateCertInfo(dto);
        checkCertNoDuplicate(dto.getCertNo(), null);

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
        normalizeCertFields(po);
        po.setCreateBy(SecurityUtils.getUserId());

        disabledPersonMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Long id, DisabledPersonSaveDTO dto) {
        // 身份证号查重（排除当前记录）
        checkIdCardDuplicate(dto.getIdCard(), id);
        // 证件信息校验与残疾人证号查重（排除当前记录自身）
        validateCertInfo(dto);
        checkCertNoDuplicate(dto.getCertNo(), id);

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
        normalizeCertFields(po);
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
     * 残疾人证号查重。
     *
     * <p>规则与身份证号保持一致：查重排除已逻辑删除的档案，即已删除档案的证号允许复用。
     * 因此数据库侧不建唯一索引（唯一索引会禁止复用，与此处业务规则冲突）。
     *
     * <p>同样存在"先查后写"的并发风险（TOCTOU）；若要禁止复用，
     * 需改为数据库唯一索引并调整此处查重逻辑（不排除软删除）。
     */
    private void checkCertNoDuplicate(String certNo, Long excludeId) {
        if (!StringUtils.hasText(certNo)) {
            return;
        }
        LambdaQueryWrapper<DisabledPersonPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledPersonPO::getCertNo, certNo.trim());
        if (excludeId != null) {
            wrapper.ne(DisabledPersonPO::getId, excludeId);
        }
        // deleted 由 @TableLogic 自动过滤（排除软删除）
        if (disabledPersonMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("残疾人证号已存在");
        }
    }

    /**
     * 证件信息业务校验。
     *
     * <p>设计原则：证件信息对历史档案<b>非必填</b>。任一字段为空表示"未录入"，
     * 不做强制；仅当字段填写时才校验其合法性。
     */
    private void validateCertInfo(DisabledPersonSaveDTO dto) {
        // 证件状态：仅允许约定的四个值
        if (StringUtils.hasText(dto.getCertStatus())) {
            String status = dto.getCertStatus().trim();
            if (!VALID_CERT_STATUSES.contains(status)) {
                throw new BusinessException(400, "证件状态不合法，可选值："
                        + String.join("、", CERT_STATUS_NORMAL, CERT_STATUS_RENEWING,
                        CERT_STATUS_CANCELLED, CERT_STATUS_LOST));
            }
        }
        // 日期先后关系：两个日期都填写时才比较，只填一个不报错
        LocalDate issueDate = dto.getCertIssueDate();
        LocalDate expireDate = dto.getCertExpireDate();
        if (issueDate != null && expireDate != null && !expireDate.isAfter(issueDate)) {
            throw new BusinessException(400, "有效期至必须晚于发证日期");
        }
    }

    /**
     * 证件筛选条件（分页查询）。
     */
    private void applyCertFilters(LambdaQueryWrapper<DisabledPersonPO> wrapper, DisabledPersonQueryDTO query) {
        if (StringUtils.hasText(query.getCertNo())) {
            wrapper.eq(DisabledPersonPO::getCertNo, query.getCertNo().trim());
        }
        if (StringUtils.hasText(query.getCertStatus())) {
            String status = query.getCertStatus().trim();
            if (!VALID_CERT_STATUSES.contains(status)) {
                throw new BusinessException(400, "证件状态不合法，可选值："
                        + String.join("、", CERT_STATUS_NORMAL, CERT_STATUS_RENEWING,
                        CERT_STATUS_CANCELLED, CERT_STATUS_LOST));
            }
            wrapper.eq(DisabledPersonPO::getCertStatus, status);
        }
        if (Boolean.TRUE.equals(query.getExpiringSoon())) {
            applyExpiringSoon(wrapper, query.getExpireWithinDays());
        }
    }

    /**
     * "即将到期"筛选。
     *
     * <p>定义：有效期至落在 [今天, 今天 + 预警天数] <b>闭区间</b>内（当天到期即算到期）。
     * <ul>
     *   <li>有效期为空：不参与预警（历史档案未录入证件时不会被筛选出来）</li>
     *   <li>已注销、已挂失：不参与预警；证件状态为空视为"未录入状态"，不排除</li>
     *   <li>预警天数：默认 30 天，范围 1~365（由 DTO 校验），非法/空值回落默认 30</li>
     * </ul>
     */
    private void applyExpiringSoon(LambdaQueryWrapper<DisabledPersonPO> wrapper, Integer expireWithinDays) {
        int days = DEFAULT_EXPIRE_WITHIN_DAYS;
        if (expireWithinDays != null && expireWithinDays >= 1 && expireWithinDays <= 365) {
            days = expireWithinDays;
        }

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);

        wrapper.isNotNull(DisabledPersonPO::getCertExpireDate);
        wrapper.ge(DisabledPersonPO::getCertExpireDate, today);
        wrapper.le(DisabledPersonPO::getCertExpireDate, deadline);
        // 状态为空（未录入）不排除，仅排除已注销、已挂失
        wrapper.and(w -> w.isNull(DisabledPersonPO::getCertStatus)
                .or()
                .notIn(DisabledPersonPO::getCertStatus, EXPIRY_EXCLUDED_CERT_STATUSES));
    }

    /**
     * 规范化证件文本字段：去除首尾空白，空串按未录入处理（存 null）。
     *
     * <p>保证"查重用的值"与"入库的值"一致（均为 trim 后）。
     */
    private void normalizeCertFields(DisabledPersonPO po) {
        po.setCertNo(normalizeText(po.getCertNo()));
        po.setCertStatus(normalizeText(po.getCertStatus()));
        po.setCertIssueOrg(normalizeText(po.getCertIssueOrg()));
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
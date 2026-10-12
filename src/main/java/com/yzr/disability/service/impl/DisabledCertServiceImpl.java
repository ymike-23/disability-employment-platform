package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.entity.dto.DisabledCertQueryDTO;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
import com.yzr.disability.entity.po.DisabledCertPO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.vo.DisabledCertVO;
import com.yzr.disability.mapper.DisabledCertMapper;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.service.DisabledCertService;
import com.yzr.disability.service.GridScopeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 证件服务。返回业务数据，由现有 Controller 统一包装为 Result。 */
@Service
public class DisabledCertServiceImpl implements DisabledCertService {

    @Autowired
    private DisabledCertMapper disabledCertMapper;

    @Autowired
    private DisabledPersonMapper disabledPersonMapper;

    @Autowired
    private GridScopeService gridScopeService;

    @Override
    public List<DisabledCertVO> list(DisabledCertQueryDTO query) {
        requirePerson(query.getPersonId(), false, "档案不存在或无权访问");
        validateCurrentFlag(query.getIsCurrent());
        String status = normalizeText(query.getCertStatus());
        validateStatus(status);

        LambdaQueryWrapper<DisabledCertPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledCertPO::getPersonId, query.getPersonId());
        String certNo = normalizeText(query.getCertNo());
        if (certNo != null) {
            wrapper.eq(DisabledCertPO::getCertNo, certNo);
        }
        if (status != null) {
            wrapper.eq(DisabledCertPO::getCertStatus, status);
        }
        if (query.getIsCurrent() != null) {
            wrapper.eq(DisabledCertPO::getIsCurrent, query.getIsCurrent());
        }
        wrapper.orderByDesc(DisabledCertPO::getIsCurrent)
                .orderByDesc(DisabledCertPO::getIssueDate)
                .orderByDesc(DisabledCertPO::getId);
        // @TableLogic 自动排除已删除证件。
        return disabledCertMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public DisabledCertVO current(Long personId) {
        requirePerson(personId, false, "档案不存在或无权访问");
        LambdaQueryWrapper<DisabledCertPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledCertPO::getPersonId, personId)
                .eq(DisabledCertPO::getIsCurrent, 1);
        List<DisabledCertPO> records = disabledCertMapper.selectList(wrapper);
        if (records.size() > 1) {
            throw new BusinessException(409, "存在多条当前证件，请核对证件数据");
        }
        return records.isEmpty() ? null : toVO(records.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DisabledCertSaveDTO dto) {
        // 在同一事务内锁定有权限的人员，避免同时新增两条当前证件。
        requirePerson(dto.getPersonId(), true, "档案不存在或无权访问");

        DisabledCertPO po = new DisabledCertPO();
        BeanUtils.copyProperties(dto, po);
        normalizeFields(po);
        po.setIsCurrent(dto.getIsCurrent() == null ? 1 : dto.getIsCurrent());
        if (po.getCertStatus() == null) {
            po.setCertStatus(CERT_STATUS_NORMAL);
        }
        validateCurrentFlag(po.getIsCurrent());
        validateStatus(po.getCertStatus());
        validateDates(po.getIssueDate(), po.getExpireDate());
        checkCertNoDuplicate(po.getCertNo(), po.getPersonId());

        if (Objects.equals(po.getIsCurrent(), 1)) {
            clearPreviousCurrent(po.getPersonId(), null);
        }
        LocalDateTime now = LocalDateTime.now();
        po.setDeleted(0);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        if (disabledCertMapper.insert(po) != 1) {
            throw new BusinessException(409, "新增证件失败");
        }
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DisabledCertSaveDTO dto) {
        DisabledCertPO existing = requireCertForWrite(id);
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId())) {
            throw new BusinessException(400, "不允许修改证件所属人员");
        }

        DisabledCertPO changes = new DisabledCertPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setPersonId(null);
        normalizeFields(changes);
        validateCurrentFlag(changes.getIsCurrent());
        validateStatus(changes.getCertStatus());
        // 部分更新也必须校验最终日期，避免只修改一个日期时绕过校验。
        validateDates(changes.getIssueDate() == null ? existing.getIssueDate() : changes.getIssueDate(),
                changes.getExpireDate() == null ? existing.getExpireDate() : changes.getExpireDate());
        checkCertNoDuplicate(changes.getCertNo() == null ? existing.getCertNo() : changes.getCertNo(),
                existing.getPersonId());

        if (Objects.equals(changes.getIsCurrent(), 1)) {
            clearPreviousCurrent(existing.getPersonId(), id);
        }
        changes.setUpdateTime(LocalDateTime.now());
        LambdaUpdateWrapper<DisabledCertPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DisabledCertPO::getId, id)
                .eq(DisabledCertPO::getPersonId, existing.getPersonId());
        if (disabledCertMapper.update(changes, wrapper) != 1) {
            throw new BusinessException(409, "证件已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DisabledCertPO existing = requireCertForWrite(id);
        LambdaQueryWrapper<DisabledCertPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledCertPO::getId, id)
                .eq(DisabledCertPO::getPersonId, existing.getPersonId());
        // @TableLogic 将删除转换为 deleted=1，不物理删除或自动启用历史证件。
        if (disabledCertMapper.delete(wrapper) != 1) {
            throw new BusinessException(409, "证件已变更，请刷新后重试");
        }
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

    private DisabledCertPO requireCertForWrite(Long id) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) {
            throw new BusinessException(400, "证件 ID 必须大于 0");
        }
        DisabledCertPO cert = disabledCertMapper.selectById(id);
        if (cert == null) {
            throw new BusinessException(admin ? 404 : 403, "证件不存在或无权操作");
        }
        // 使用数据库里的 personId 校验权限，不能信任修改入参中的 personId。
        requirePerson(cert.getPersonId(), true, "证件不存在或无权操作");
        // 获取人员锁后重新读取，处理等待锁期间证件已被删除的情况。
        DisabledCertPO latest = disabledCertMapper.selectById(id);
        if (latest == null || !Objects.equals(latest.getPersonId(), cert.getPersonId())) {
            throw new BusinessException(admin ? 404 : 403, "证件不存在或无权操作");
        }
        return latest;
    }

    private void clearPreviousCurrent(Long personId, Long excludeId) {
        LambdaUpdateWrapper<DisabledCertPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DisabledCertPO::getPersonId, personId)
                .eq(DisabledCertPO::getIsCurrent, 1);
        if (excludeId != null) {
            wrapper.ne(DisabledCertPO::getId, excludeId);
        }
        wrapper.set(DisabledCertPO::getIsCurrent, 0)
                .set(DisabledCertPO::getUpdateTime, LocalDateTime.now());
        disabledCertMapper.update(null, wrapper);
    }

    private void checkCertNoDuplicate(String certNo, Long personId) {
        if (!StringUtils.hasText(certNo)) {
            return;
        }
        LambdaQueryWrapper<DisabledCertPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisabledCertPO::getCertNo, certNo.trim())
                .ne(DisabledCertPO::getPersonId, personId)
                .inSql(DisabledCertPO::getPersonId, "SELECT id FROM disabled_person WHERE deleted = 0");
        // 同一人员的历史记录可以保留相同证号，已删除档案不占用证号。
        if (disabledCertMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "残疾人证号已存在");
        }
    }

    private void validateCurrentFlag(Integer isCurrent) {
        if (isCurrent != null && isCurrent != 0 && isCurrent != 1) {
            throw new BusinessException(400, "当前证件标识只能为 0 或 1");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !VALID_CERT_STATUSES.contains(status)) {
            throw new BusinessException(400, "证件状态不合法，可选值：正常、换证中、注销、挂失");
        }
    }

    private void validateDates(LocalDate issueDate, LocalDate expireDate) {
        if (issueDate != null && expireDate != null && !expireDate.isAfter(issueDate)) {
            throw new BusinessException(400, "有效期至必须晚于发证日期");
        }
    }

    private void normalizeFields(DisabledCertPO po) {
        po.setCertNo(normalizeText(po.getCertNo()));
        po.setCertType(normalizeText(po.getCertType()));
        po.setIssueOrg(normalizeText(po.getIssueOrg()));
        po.setDisabilityType(normalizeText(po.getDisabilityType()));
        po.setDisabilityLevel(normalizeText(po.getDisabilityLevel()));
        po.setCertStatus(normalizeText(po.getCertStatus()));
        po.setRemark(normalizeText(po.getRemark()));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DisabledCertVO toVO(DisabledCertPO po) {
        DisabledCertVO vo = new DisabledCertVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }
}

package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingCertificateQueryDTO;
import com.yzr.disability.entity.po.DisabledPersonPO;
import com.yzr.disability.entity.po.TrainingCertificatePO;
import com.yzr.disability.entity.vo.TrainingCertificateVO;
import com.yzr.disability.mapper.DisabledPersonMapper;
import com.yzr.disability.mapper.TrainingCertificateMapper;
import com.yzr.disability.service.GridScopeService;
import com.yzr.disability.service.TrainingCertificateService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
public class TrainingCertificateServiceImpl implements TrainingCertificateService {
    @Autowired private TrainingCertificateMapper trainingCertificateMapper;
    @Autowired private DisabledPersonMapper disabledPersonMapper;
    @Autowired private GridScopeService gridScopeService;

    @Override
    public PageResult<TrainingCertificateVO> page(TrainingCertificateQueryDTO query) {
        boolean admin = gridScopeService.isAdmin();
        if (query == null) throw new BusinessException(400, "查询条件不能为空");
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        if (current < 1 || size < 1 || size > 100) throw new BusinessException(400, "分页参数无效");
        if (query.getCertificateNo() != null && query.getCertificateNo().length() > 50)
            throw new BusinessException(400, "证书编号长度不能超过50");
        if (query.getPersonId() != null) requirePerson(query.getPersonId());
        LambdaQueryWrapper<TrainingCertificatePO> wrapper = new LambdaQueryWrapper<>();
        if (!admin) {
            List<Long> grids = gridScopeService.getCurrentGridIds();
            if (grids.size() > 1) throw new BusinessException(403, GridScopeService.MULTI_GRID_MESSAGE);
            if (grids.isEmpty()) return new PageResult<>(List.of(), 0, current, size, 0);
            wrapper.exists("SELECT 1 FROM disabled_person p WHERE p.id = training_certificate.person_id"
                    + " AND p.deleted = 0 AND p.grid_id = {0}", grids.getFirst());
        } else {
            wrapper.exists("SELECT 1 FROM disabled_person p WHERE p.id = training_certificate.person_id AND p.deleted = 0");
        }
        if (query.getPersonId() != null) wrapper.eq(TrainingCertificatePO::getPersonId, query.getPersonId());
        if (query.getCertificateNo() != null && !query.getCertificateNo().isBlank())
            wrapper.like(TrainingCertificatePO::getCertificateNo, query.getCertificateNo().trim());
        wrapper.orderByDesc(TrainingCertificatePO::getIssueDate).orderByDesc(TrainingCertificatePO::getId);
        Page<TrainingCertificatePO> result = trainingCertificateMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public TrainingCertificateVO detail(Long id) {
        boolean admin = gridScopeService.isAdmin();
        if (id == null || id <= 0) throw new BusinessException(400, "证书ID必须大于0");
        TrainingCertificatePO certificate = trainingCertificateMapper.selectById(id);
        if (certificate == null) throw new BusinessException(admin ? 404 : 403, "证书不存在或无权访问");
        requirePerson(certificate.getPersonId());
        return toVO(certificate);
    }

    @Override
    public List<TrainingCertificateVO> byPerson(Long personId) {
        requirePerson(personId);
        return trainingCertificateMapper.selectList(new LambdaQueryWrapper<TrainingCertificatePO>()
                .eq(TrainingCertificatePO::getPersonId, personId)
                .orderByDesc(TrainingCertificatePO::getIssueDate).orderByDesc(TrainingCertificatePO::getId))
                .stream().map(this::toVO).toList();
    }

    private void requirePerson(Long personId) {
        boolean admin = gridScopeService.isAdmin();
        if (personId == null || personId <= 0) throw new BusinessException(400, "人员ID必须大于0");
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        DisabledPersonPO person = disabledPersonMapper.selectById(personId);
        if (person == null || (!admin && !Objects.equals(gridId, person.getGridId())))
            throw new BusinessException(admin ? 404 : 403, "人员不存在或无权访问");
    }

    private TrainingCertificateVO toVO(TrainingCertificatePO po) {
        TrainingCertificateVO vo = new TrainingCertificateVO();
        BeanUtils.copyProperties(po, vo);
        return vo;
    }
}


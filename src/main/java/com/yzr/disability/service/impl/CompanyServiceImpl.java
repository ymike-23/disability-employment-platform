package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.CompanyQueryDTO;
import com.yzr.disability.entity.dto.CompanySaveDTO;
import com.yzr.disability.entity.po.CompanyPO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.vo.CompanyVO;
import com.yzr.disability.mapper.CompanyMapper;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.service.CompanyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompanyServiceImpl implements CompanyService {

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private SysDictMapper sysDictMapper;

    @Override
    public PageResult<CompanyVO> page(CompanyQueryDTO query) {
        requireReadAccess();
        if (query == null) {
            throw new BusinessException(400, "查询条件不能为空");
        }
        validateLength(query.getCompanyName(), 100, "企业名称");
        validateLength(query.getDistrict(), 20, "地区字典值");
        validateLength(query.getIndustry(), 50, "行业");
        validateStatus(query.getStatus());
        validateDistrict(query.getDistrict());
        long current = query.getCurrent() == null ? 1L : query.getCurrent();
        long size = query.getSize() == null ? 10L : query.getSize();
        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "页码必须大于0，每页条数必须在1到100之间");
        }
        LambdaQueryWrapper<CompanyPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getCompanyName())) {
            wrapper.like(CompanyPO::getCompanyName, query.getCompanyName().trim());
        }
        if (query.getDistrict() != null) {
            wrapper.eq(CompanyPO::getDistrict, query.getDistrict().trim());
        }
        if (StringUtils.hasText(query.getIndustry())) {
            wrapper.like(CompanyPO::getIndustry, query.getIndustry().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(CompanyPO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(CompanyPO::getCreateTime).orderByDesc(CompanyPO::getId);
        Page<CompanyPO> result = companyMapper.selectPage(new Page<>(current, size), wrapper);
        List<CompanyVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public CompanyVO detail(Long id) {
        requireReadAccess();
        validateId(id);
        CompanyPO company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(404, "企业不存在或已删除");
        }
        return toVO(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CompanySaveDTO dto) {
        Long userId = requireWriteAccess();
        validateCompany(dto);
        CompanyPO company = new CompanyPO();
        BeanUtils.copyProperties(dto, company);
        normalizeFields(company);
        company.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        company.setDeleted(0);
        company.setCreateBy(userId);
        company.setUpdateBy(userId);
        LocalDateTime now = LocalDateTime.now();
        company.setCreateTime(now);
        company.setUpdateTime(now);
        if (companyMapper.insert(company) != 1) {
            throw new BusinessException(409, "新增企业失败");
        }
        return company.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CompanySaveDTO dto) {
        Long userId = requireWriteAccess();
        requireCompanyForUpdate(id);
        validateCompany(dto);
        CompanyPO changes = new CompanyPO();
        BeanUtils.copyProperties(dto, changes);
        normalizeFields(changes);
        changes.setId(id);
        changes.setUpdateBy(userId);
        changes.setUpdateTime(LocalDateTime.now());
        if (companyMapper.updateById(changes) != 1) {
            throw new BusinessException(409, "企业已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = requireWriteAccess();
        requireCompanyForUpdate(id);
        if (companyMapper.selectActiveJobForUpdate(id) != null) {
            throw new BusinessException(409, "企业仍有关联的未删除岗位，不能删除");
        }
        CompanyPO audit = new CompanyPO();
        audit.setId(id);
        audit.setUpdateBy(userId);
        audit.setUpdateTime(LocalDateTime.now());
        if (companyMapper.updateById(audit) != 1 || companyMapper.deleteById(id) != 1) {
            throw new BusinessException(409, "企业已变更，请刷新后重试");
        }
    }

    private CompanyPO requireCompanyForUpdate(Long id) {
        validateId(id);
        CompanyPO company = companyMapper.selectForUpdate(id);
        if (company == null) {
            throw new BusinessException(404, "企业不存在或已删除");
        }
        return company;
    }

    private void requireReadAccess() {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("BUSINESS")) {
            throw new BusinessException(403, "当前账号无企业访问权限");
        }
    }

    private Long requireWriteAccess() {
        if (!SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException(403, "仅管理员可以维护企业");
        }
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        return userId;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "企业ID必须大于0");
        }
    }

    private void validateCompany(CompanySaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCompanyName())) {
            throw new BusinessException(400, "企业名称不能为空");
        }
        validateLength(dto.getCompanyName(), 100, "企业名称");
        validateLength(dto.getContactPerson(), 50, "联系人");
        validateLength(dto.getContactPhone(), 20, "联系电话");
        validateLength(dto.getAddress(), 255, "地址");
        validateLength(dto.getDistrict(), 20, "地区字典值");
        validateLength(dto.getIndustry(), 50, "行业");
        validateLength(dto.getRemark(), 500, "备注");
        validateStatus(dto.getStatus());
        validateDistrict(dto.getDistrict());
    }

    private void validateLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new BusinessException(400, fieldName + "长度不能超过" + max);
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(400, "企业状态仅允许0或1");
        }
    }

    private void validateDistrict(String district) {
        if (district == null) {
            return;
        }
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, "district")
                .eq(SysDictPO::getDictValue, district.trim())
                .eq(SysDictPO::getStatus, 1)
                .select(SysDictPO::getDictValue);
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        if (!StringUtils.hasText(district) || dict == null || !district.trim().equals(dict.getDictValue())) {
            throw new BusinessException(400, "地区字典值不存在或已停用");
        }
    }

    private void normalizeFields(CompanyPO company) {
        company.setCompanyName(trim(company.getCompanyName()));
        company.setContactPerson(trim(company.getContactPerson()));
        company.setContactPhone(trim(company.getContactPhone()));
        company.setAddress(trim(company.getAddress()));
        company.setDistrict(trim(company.getDistrict()));
        company.setIndustry(trim(company.getIndustry()));
        company.setRemark(trim(company.getRemark()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private CompanyVO toVO(CompanyPO company) {
        CompanyVO vo = new CompanyVO();
        BeanUtils.copyProperties(company, vo);
        return vo;
    }
}

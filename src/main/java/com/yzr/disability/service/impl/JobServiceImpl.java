package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.dto.JobSaveDTO;
import com.yzr.disability.entity.po.CompanyPO;
import com.yzr.disability.entity.po.JobPO;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.vo.JobDetailVO;
import com.yzr.disability.entity.vo.JobVO;
import com.yzr.disability.mapper.CompanyMapper;
import com.yzr.disability.mapper.JobMapper;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.service.JobService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** 岗位模块：岗位维护和企业归属一致性校验。 */
@Service
public class JobServiceImpl implements JobService {

    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private CompanyMapper companyMapper;
    @Autowired
    private SysDictMapper sysDictMapper;

    @Override
    public PageResult<JobVO> page(JobQueryDTO query) {
        requireRole(false);
        if (query == null) {
            throw new BusinessException(400, "查询条件不能为空");
        }
        validateText(query.getJobName(), 100, "岗位名称");
        validateEducation(query.getEducationRequirement());
        validateStatus(query.getStatus());
        if (query.getCompanyId() != null) {
            validateId(query.getCompanyId(), "企业");
        }
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(400, "页码必须大于0，每页条数必须在1至100之间");
        }
        if (query.getJobName() != null) {
            query.setJobName(query.getJobName().trim());
        }
        IPage<JobVO> result = jobMapper.selectJobPage(new Page<>(current, size), query);
        return PageResult.of(result);
    }

    @Override
    public JobDetailVO detail(Long id) {
        requireRole(false);
        validateId(id, "岗位");
        JobDetailVO result = jobMapper.selectJobDetail(id);
        if (result == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(JobSaveDTO dto) {
        requireRole(true);
        validateJob(dto);
        requireEnabledCompany(companyMapper.selectForUpdate(dto.getCompanyId()));
        JobPO po = new JobPO();
        BeanUtils.copyProperties(dto, po);
        po.setJobName(dto.getJobName().trim());
        po.setHeadcount(dto.getHeadcount() == null ? 1 : dto.getHeadcount());
        po.setStatus(dto.getStatus() == null ? "0" : dto.getStatus());
        po.setDeleted(0);
        po.setCreateBy(SecurityUtils.getUserId());
        po.setUpdateBy(SecurityUtils.getUserId());
        LocalDateTime now = LocalDateTime.now();
        po.setCreateTime(now);
        po.setUpdateTime(now);
        if (jobMapper.insert(po) != 1) {
            throw new BusinessException(409, "新增岗位失败");
        }
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, JobSaveDTO dto) {
        requireRole(true);
        validateJob(dto);
        JobPO observed = requireJob(id);
        Long oldCompanyId = observed.getCompanyId();
        Long targetCompanyId = dto.getCompanyId();
        CompanyPO targetCompany;
        // 企业统一按ID升序加锁，再锁岗位，与企业删除保持相同锁序。
        if (oldCompanyId.equals(targetCompanyId)) {
            targetCompany = companyMapper.selectForUpdate(oldCompanyId);
        } else if (oldCompanyId < targetCompanyId) {
            companyMapper.selectForUpdate(oldCompanyId);
            targetCompany = companyMapper.selectForUpdate(targetCompanyId);
        } else {
            targetCompany = companyMapper.selectForUpdate(targetCompanyId);
            companyMapper.selectForUpdate(oldCompanyId);
        }
        lockObservedJob(observed);
        if (!oldCompanyId.equals(targetCompanyId)) {
            if (jobMapper.selectEmploymentReferenceForUpdate(id) != null) {
                throw new BusinessException(409, "岗位已有就业服务历史，不允许变更所属企业");
            }
            requireEnabledCompany(targetCompany);
        }
        JobPO changes = new JobPO();
        BeanUtils.copyProperties(dto, changes);
        changes.setId(id);
        changes.setJobName(dto.getJobName().trim());
        setUpdateAudit(changes);
        if (jobMapper.updateById(changes) != 1) {
            throw new BusinessException(409, "岗位已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRole(true);
        JobPO observed = requireJob(id);
        companyMapper.selectForUpdate(observed.getCompanyId());
        lockObservedJob(observed);
        JobPO audit = new JobPO();
        audit.setId(id);
        setUpdateAudit(audit);
        if (jobMapper.updateById(audit) != 1 || jobMapper.deleteById(id) != 1) {
            throw new BusinessException(409, "岗位已变更，请刷新后重试");
        }
    }

    private JobPO requireJob(Long id) {
        validateId(id, "岗位");
        JobPO po = jobMapper.selectById(id);
        if (po == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return po;
    }

    private void lockObservedJob(JobPO observed) {
        JobPO locked = jobMapper.selectForUpdate(observed.getId());
        if (locked == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        if (!observed.getCompanyId().equals(locked.getCompanyId())) {
            throw new BusinessException(409, "岗位所属企业已变更，请刷新后重试");
        }
    }

    private void requireEnabledCompany(CompanyPO company) {
        if (company == null) {
            throw new BusinessException(404, "企业不存在");
        }
        if (!Integer.valueOf(1).equals(company.getStatus())) {
            throw new BusinessException(409, "企业未正常启用，不能新增或转入岗位");
        }
    }

    private void validateJob(JobSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "岗位参数不能为空");
        }
        validateId(dto.getCompanyId(), "企业");
        if (!StringUtils.hasText(dto.getJobName())) {
            throw new BusinessException(400, "岗位名称不能为空");
        }
        validateText(dto.getJobName(), 100, "岗位名称");
        validateText(dto.getSalary(), 50, "薪资");
        validateText(dto.getRemark(), 500, "备注");
        if (dto.getRequirement() != null
                && dto.getRequirement().getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException(400, "岗位要求UTF-8编码不能超过65535字节");
        }
        if (dto.getHeadcount() != null && dto.getHeadcount() <= 0) {
            throw new BusinessException(400, "招聘人数必须大于0");
        }
        validateStatus(dto.getStatus());
        validateEducation(dto.getEducationRequirement());
    }

    private void validateEducation(String value) {
        if (value == null) {
            return;
        }
        validateText(value, 20, "学历要求字典值");
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, "education")
                .eq(SysDictPO::getDictValue, value)
                .eq(SysDictPO::getStatus, 1)
                .select(SysDictPO::getDictValue);
        SysDictPO dict = sysDictMapper.selectOne(wrapper);
        if (dict == null || !value.equals(dict.getDictValue())) {
            throw new BusinessException(400, "学历要求字典值不存在或已停用");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !"0".equals(status) && !"1".equals(status)) {
            throw new BusinessException(400, "岗位状态只能为0或1");
        }
    }

    private void validateText(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400, name + "长度不能超过" + maxLength);
        }
    }

    private void validateId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, name + "ID必须大于0");
        }
    }

    private void requireRole(boolean write) {
        if (!SecurityUtils.hasRole("ADMIN") && (write || !SecurityUtils.hasRole("BUSINESS"))) {
            throw new BusinessException(403, write ? "仅管理员可维护岗位" : "当前账号无岗位访问权限");
        }
        if (write && SecurityUtils.getUserId() == null) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
    }

    private void setUpdateAudit(JobPO po) {
        po.setUpdateBy(SecurityUtils.getUserId());
        po.setUpdateTime(LocalDateTime.now());
    }
}

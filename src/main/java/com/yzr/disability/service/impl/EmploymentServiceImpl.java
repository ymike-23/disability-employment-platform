package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.*;
import com.yzr.disability.entity.po.*;
import com.yzr.disability.entity.vo.*;
import com.yzr.disability.mapper.*;
import com.yzr.disability.service.EmploymentService;
import com.yzr.disability.service.GridScopeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 就业服务模块：人员网格授权、应聘事件流转及就业状态的事务处理。 */
@Service
public class EmploymentServiceImpl implements EmploymentService {
    private static final String DENIED = "就业服务记录不存在或无权访问";
    private static final Set<String> TYPES = Set.of("1", "2", "3", "4", "5", "6", "7");
    @Autowired private EmploymentServiceMapper employmentServiceMapper;
    @Autowired private DisabledPersonMapper disabledPersonMapper;
    @Autowired private JobMapper jobMapper;
    @Autowired private CompanyMapper companyMapper;
    @Autowired private SysDictMapper sysDictMapper;
    @Autowired private GridScopeService gridScopeService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<EmploymentServiceVO> page(EmploymentServiceQueryDTO query) {
        boolean admin = gridScopeService.isAdmin();
        if (query == null) throw new BusinessException(400, "查询条件不能为空");
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        validatePage(current, size);
        if (query.getPersonId() != null) validateId(query.getPersonId());
        if (query.getJobId() != null) validateId(query.getJobId());
        if (query.getRootServiceId() != null) validateId(query.getRootServiceId());
        if (query.getServiceType() != null) validateType(query.getServiceType());
        if (query.getStatus() != null) validateDict("employment_service_status", query.getStatus());
        Long gridId = null;
        if (!admin) {
            List<Long> grids = gridScopeService.getCurrentGridIds();
            if (grids.size() > 1) throw new BusinessException(403, GridScopeService.MULTI_GRID_MESSAGE);
            if (grids.isEmpty()) return new PageResult<>(List.of(), 0, current, size, 0);
            gridId = grids.getFirst();
            if (query.getPersonId() != null) requirePerson(query.getPersonId(), false);
        }
        PageResult<EmploymentServiceVO> result = PageResult.of(employmentServiceMapper.selectServicePage(
                new Page<>(current, size), query, gridId));
        decorate(result.getRecords());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EmploymentServiceVO detail(Long id) {
        boolean admin = gridScopeService.isAdmin();
        validateId(id);
        EmploymentServiceVO event = employmentServiceMapper.selectServiceDetail(id,
                admin ? null : gridScopeService.requireSingleGridId());
        if (event == null) throw inaccessible();
        decorate(List.of(event));
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public EmploymentPersonVO person(Long personId) {
        DisabledPersonPO person = requirePerson(personId, false);
        List<EmploymentServiceVO> records = employmentServiceMapper.selectPersonHistory(personId,
                gridScopeService.isAdmin() ? null : gridScopeService.requireSingleGridId());
        decorate(records);
        EmploymentPersonVO result = new EmploymentPersonVO();
        result.setPersonId(personId);
        result.setEmploymentStatus(person.getEmploymentStatus());
        result.setRecords(records);
        return result;
    }

    @Override
    public PageResult<JobVO> candidateJobs(JobQueryDTO query) {
        gridScopeService.isAdmin();
        if (query == null) throw new BusinessException(400, "查询条件不能为空");
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        validatePage(current, size);
        if (query.getCompanyId() != null) validateId(query.getCompanyId());
        validateText(query.getJobName(), 100, "岗位名称");
        if (query.getEducationRequirement() != null) validateDict("education", query.getEducationRequirement());
        if (query.getStatus() != null && !"0".equals(query.getStatus()))
            throw new BusinessException(400, "候选岗位仅支持招聘中状态0");
        return PageResult.of(employmentServiceMapper.selectCandidateJobs(new Page<>(current, size), query));
    }

    @Override
    public Set<Long> ongoingJobIds(Long personId) {
        requirePerson(personId, false);
        List<EmploymentServicePO> events = employmentServiceMapper.selectList(
                new LambdaQueryWrapper<EmploymentServicePO>().eq(EmploymentServicePO::getPersonId, personId));
        Set<Long> jobs = new HashSet<>();
        events.stream().filter(e -> e.getRootServiceId() == null && !ended(stage(e.getId(), events)))
                .forEach(e -> jobs.add(e.getJobId()));
        return jobs;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long create(EmploymentServiceSaveDTO dto) {
        gridScopeService.isAdmin();
        validateSave(dto);
        List<EmploymentServicePO> events;
        if (dto.getRootServiceId() == null) {
            if (!TYPE_RECOMMEND.equals(dto.getServiceType()))
                throw new BusinessException(400, "新批次必须从岗位推荐开始");
            requirePerson(dto.getPersonId(), true);
            requireRecruitingJob(dto.getJobId());
            // shortcut: 去重依赖统一人员行锁，新增绕过本服务的写入口时需重新设计数据库兜底。
            events = employmentServiceMapper.selectPersonForUpdate(dto.getPersonId());
            if (events.stream().filter(e -> e.getRootServiceId() == null)
                    .anyMatch(e -> Objects.equals(e.getJobId(), dto.getJobId()) && !ended(stage(e.getId(), events))))
                throw new BusinessException(409, "该人员在此岗位已有未结束的应聘批次");
            validateActualDate(dto.getServiceDate() == null ? LocalDate.now() : dto.getServiceDate(), null);
        } else {
            events = lockEvents(dto.getRootServiceId());
            EmploymentServicePO root = event(dto.getRootServiceId(), events);
            requireRoot(root);
            if (!Objects.equals(root.getPersonId(), dto.getPersonId()) || !Objects.equals(root.getJobId(), dto.getJobId()))
                throw new BusinessException(400, "事件人员和岗位必须与批次一致");
            requireAllowed(dto.getServiceType(), stage(root.getId(), events));
            if (events.stream().anyMatch(e -> Objects.equals(e.getRootServiceId(), root.getId())
                    && dto.getServiceType().equals(e.getServiceType()) && STATUS_PENDING.equals(e.getStatus())))
                throw new BusinessException(409, "该批次已有同类待办事件");
            if (dto.getServiceDate() != null && dto.getServiceDate().isBefore(root.getServiceDate()))
                throw new BusinessException(400, "服务日期不能早于推荐日期");
        }
        EmploymentServicePO saved = new EmploymentServicePO();
        BeanUtils.copyProperties(dto, saved);
        boolean root = dto.getRootServiceId() == null;
        saved.setStatus(root ? STATUS_COMPLETED : STATUS_PENDING);
        validateDict("employment_service_status", saved.getStatus());
        if (root) {
            validateDict("employment_service_result", RESULT_SUCCESS);
            saved.setResult(RESULT_SUCCESS);
        }
        saved.setServiceDate(dto.getServiceDate() == null ? LocalDate.now() : dto.getServiceDate());
        saved.setDeleted(0);
        saved.setCreateBy(SecurityUtils.getUserId());
        saved.setCreateTime(LocalDateTime.now());
        audit(saved);
        if (employmentServiceMapper.insert(saved) != 1) throw new BusinessException(409, "新增就业服务失败");
        events.add(saved);
        syncEmployment(saved.getPersonId(), events);
        return saved.getId();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void update(Long id, EmploymentServiceSaveDTO dto) {
        List<EmploymentServicePO> events = lockEvents(id);
        EmploymentServicePO existing = event(id, events);
        requirePending(existing);
        validateSave(dto);
        if (!Objects.equals(existing.getPersonId(), dto.getPersonId())
                || !Objects.equals(existing.getJobId(), dto.getJobId())
                || !Objects.equals(existing.getRootServiceId(), dto.getRootServiceId())
                || !Objects.equals(existing.getServiceType(), dto.getServiceType()))
            throw new BusinessException(400, "不允许修改事件所属人员、岗位、批次或类型");
        if (dto.getServiceDate() != null && dto.getServiceDate().isBefore(event(existing.getRootServiceId(), events).getServiceDate()))
            throw new BusinessException(400, "服务日期不能早于推荐日期");
        EmploymentServicePO changes = new EmploymentServicePO();
        changes.setId(id);
        changes.setServiceDate(dto.getServiceDate());
        changes.setContent(dto.getContent());
        changes.setRemark(dto.getRemark());
        audit(changes);
        if (employmentServiceMapper.updateById(changes) != 1) throw new BusinessException(409, "事件已变更");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void complete(Long id, EmploymentServiceCompleteDTO dto) {
        List<EmploymentServicePO> events = lockEvents(id);
        EmploymentServicePO existing = event(id, events);
        requirePending(existing);
        if (dto == null || dto.getResult() == null
                || !Set.of(RESULT_SUCCESS, RESULT_FAILURE, RESULT_WITHDRAWN).contains(dto.getResult()))
            throw new BusinessException(400, "结果字典值无效");
        validateDict("employment_service_result", dto.getResult());
        validateDict("employment_service_status", STATUS_COMPLETED);
        validateContent(dto.getContent(), dto.getRemark());
        Long root = existing.getRootServiceId();
        requireAllowed(existing.getServiceType(), stage(root, events));
        if (TYPE_ONBOARD.equals(existing.getServiceType()) && RESULT_SUCCESS.equals(dto.getResult())
                && events.stream().filter(e -> e.getRootServiceId() == null && !e.getId().equals(root))
                .anyMatch(e -> STAGE_EMPLOYED.equals(stage(e.getId(), events))))
            throw new BusinessException(409, "该人员已有在职批次，请先登记离职");
        LocalDate date = dto.getServiceDate() == null ? LocalDate.now() : dto.getServiceDate();
        LocalDate last = events.stream().filter(e -> root.equals(rootId(e)) && STATUS_COMPLETED.equals(e.getStatus()))
                .map(EmploymentServicePO::getServiceDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        validateActualDate(date, last);
        existing.setStatus(STATUS_COMPLETED);
        existing.setResult(dto.getResult());
        existing.setServiceDate(date);
        if (dto.getContent() != null) existing.setContent(dto.getContent());
        if (dto.getRemark() != null) existing.setRemark(dto.getRemark());
        audit(existing);
        if (employmentServiceMapper.updateById(existing) != 1) throw new BusinessException(409, "事件已变更");
        syncEmployment(existing.getPersonId(), events);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void cancel(Long id) {
        List<EmploymentServicePO> events = lockEvents(id);
        EmploymentServicePO existing = event(id, events);
        requirePending(existing);
        validateDict("employment_service_status", STATUS_CANCELLED);
        existing.setStatus(STATUS_CANCELLED);
        audit(existing);
        if (employmentServiceMapper.updateById(existing) != 1) throw new BusinessException(409, "事件已变更");
    }

    private DisabledPersonPO requirePerson(Long id, boolean write) {
        boolean admin = gridScopeService.isAdmin();
        validateId(id);
        Long gridId = admin ? null : gridScopeService.requireSingleGridId();
        if (write && disabledPersonMapper.selectAccessibleForUpdate(id, gridId) == null) throw inaccessible();
        DisabledPersonPO person = disabledPersonMapper.selectById(id);
        if (person == null || (!admin && !Objects.equals(gridId, person.getGridId()))) throw inaccessible();
        return person;
    }

    private List<EmploymentServicePO> lockEvents(Long id) {
        gridScopeService.isAdmin();
        validateId(id);
        EmploymentServicePO observed = employmentServiceMapper.selectById(id);
        if (observed == null) throw inaccessible();
        requirePerson(observed.getPersonId(), true);
        List<EmploymentServicePO> events = employmentServiceMapper.selectPersonForUpdate(observed.getPersonId());
        event(id, events);
        return events;
    }

    private EmploymentServicePO event(Long id, List<EmploymentServicePO> events) {
        return events.stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow(this::inaccessible);
    }

    private void requireRecruitingJob(Long id) {
        JobPO observed = jobMapper.selectById(id);
        if (observed == null) throw new BusinessException(404, "岗位不存在");
        // 与岗位维护保持企业→岗位锁序，历史批次不依赖企业当前启停状态。
        CompanyPO company = companyMapper.selectForUpdate(observed.getCompanyId());
        JobPO job = jobMapper.selectForUpdate(id);
        if (job == null || company == null) throw new BusinessException(404, "岗位或企业不存在");
        if (!Objects.equals(job.getCompanyId(), observed.getCompanyId())) throw new BusinessException(409, "岗位企业已变更，请重试");
        if (!Integer.valueOf(1).equals(company.getStatus()) || !"0".equals(job.getStatus()))
            throw new BusinessException(409, "仅可推荐启用企业下招聘中的岗位");
    }

    private void requireRoot(EmploymentServicePO root) {
        if (root.getRootServiceId() != null || !TYPE_RECOMMEND.equals(root.getServiceType()))
            throw new BusinessException(400, "rootServiceId必须指向首次推荐记录");
    }

    private void requirePending(EmploymentServicePO event) {
        if (!STATUS_PENDING.equals(event.getStatus())) throw new BusinessException(409, "仅待办事件允许此操作");
    }

    private void requireAllowed(String type, String stage) {
        boolean allowed = switch (type) {
            case TYPE_CONTACT -> Set.of(STAGE_RECOMMENDED, STAGE_CONTACTED, STAGE_INTERVIEWED).contains(stage);
            case TYPE_INTERVIEW -> Set.of(STAGE_CONTACTED, STAGE_INTERVIEWED).contains(stage);
            case TYPE_OFFER -> STAGE_INTERVIEWED.equals(stage);
            case TYPE_ONBOARD -> STAGE_OFFERED.equals(stage);
            case TYPE_LEAVE -> STAGE_EMPLOYED.equals(stage);
            case TYPE_FOLLOWUP -> Set.of(STAGE_EMPLOYED, STAGE_LEFT).contains(stage);
            default -> false;
        };
        if (!allowed) throw new BusinessException(409, "当前批次进度不允许办理此类事件");
    }

    private Long rootId(EmploymentServicePO event) {
        return event.getRootServiceId() == null ? event.getId() : event.getRootServiceId();
    }

    private boolean ended(String stage) {
        return STAGE_CLOSED.equals(stage) || STAGE_LEFT.equals(stage);
    }

    private String stage(Long root, List<EmploymentServicePO> events) {
        List<EmploymentServicePO> completed = events.stream().filter(e -> root.equals(rootId(e))
                && STATUS_COMPLETED.equals(e.getStatus())).toList();
        if (success(completed, TYPE_LEAVE)) return STAGE_LEFT;
        if (success(completed, TYPE_ONBOARD)) return STAGE_EMPLOYED;
        if (completed.stream().anyMatch(e -> !RESULT_SUCCESS.equals(e.getResult())
                && (Set.of(TYPE_INTERVIEW, TYPE_OFFER, TYPE_ONBOARD).contains(e.getServiceType())
                || (TYPE_CONTACT.equals(e.getServiceType()) && RESULT_WITHDRAWN.equals(e.getResult())))))
            return STAGE_CLOSED;
        if (success(completed, TYPE_OFFER)) return STAGE_OFFERED;
        if (success(completed, TYPE_INTERVIEW)) return STAGE_INTERVIEWED;
        if (success(completed, TYPE_CONTACT)) return STAGE_CONTACTED;
        return STAGE_RECOMMENDED;
    }

    private boolean success(List<EmploymentServicePO> events, String type) {
        return events.stream().anyMatch(e -> type.equals(e.getServiceType()) && RESULT_SUCCESS.equals(e.getResult()));
    }

    private void syncEmployment(Long personId, List<EmploymentServicePO> events) {
        List<String> stages = events.stream().filter(e -> e.getRootServiceId() == null)
                .map(e -> stage(e.getId(), events)).toList();
        // NULL只在尚未发生核实事件时保留，不能把所有历史人员默认判为待就业。
        if (stages.isEmpty()) return;
        String status = stages.contains(STAGE_EMPLOYED) ? "2"
                : stages.stream().anyMatch(s -> !ended(s)) ? "1" : stages.contains(STAGE_LEFT) ? "3" : "0";
        validateDict("employment_status", status);
        if (employmentServiceMapper.updatePersonEmploymentStatus(personId, status, SecurityUtils.getUserId()) != 1)
            throw new BusinessException(409, "人员就业状态更新失败");
    }

    private void decorate(List<EmploymentServiceVO> records) {
        if (records.isEmpty()) return;
        Set<Long> people = new HashSet<>();
        records.forEach(e -> people.add(e.getPersonId()));
        List<EmploymentServicePO> events = employmentServiceMapper.selectList(new LambdaQueryWrapper<EmploymentServicePO>()
                .in(EmploymentServicePO::getPersonId, people));
        records.forEach(e -> e.setBatchStage(stage(e.getRootServiceId() == null ? e.getId() : e.getRootServiceId(), events)));
    }

    private BusinessException inaccessible() {
        return new BusinessException(gridScopeService.isAdmin() ? 404 : 403, DENIED);
    }

    private void validateSave(EmploymentServiceSaveDTO dto) {
        if (dto == null) throw new BusinessException(400, "服务参数不能为空");
        validateId(dto.getPersonId());
        validateId(dto.getJobId());
        if (dto.getRootServiceId() != null) validateId(dto.getRootServiceId());
        validateType(dto.getServiceType());
        validateContent(dto.getContent(), dto.getRemark());
    }

    private void validateType(String type) {
        if (type == null || !TYPES.contains(type)) throw new BusinessException(400, "服务类型无效");
        validateDict("service_type", type);
    }

    private void validateDict(String type, String value) {
        if (value == null || value.length() > 100 || employmentDictCount(type, value) != 1)
            throw new BusinessException(400, "字典值不存在或已停用：" + type);
    }

    private long employmentDictCount(String type, String value) {
        return sysDictMapper.selectCount(new LambdaQueryWrapper<SysDictPO>().eq(SysDictPO::getDictType, type)
                .eq(SysDictPO::getDictValue, value).eq(SysDictPO::getStatus, 1));
    }

    private void validateContent(String content, String remark) {
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > 65535)
            throw new BusinessException(400, "服务内容UTF-8编码不能超过65535字节");
        validateText(remark, 500, "备注");
    }

    private void validateText(String value, int length, String name) {
        if (value != null && value.length() > length) throw new BusinessException(400, name + "长度超限");
    }

    private void validateActualDate(LocalDate date, LocalDate previous) {
        if (date.isAfter(LocalDate.now()) || (previous != null && date.isBefore(previous)))
            throw new BusinessException(400, "实际服务日期不能晚于今天或早于已完成事件");
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(400, "ID必须大于0");
    }

    private void validatePage(long current, long size) {
        if (current < 1 || size < 1 || size > 100) throw new BusinessException(400, "页码必须大于0，每页条数1至100");
    }

    private void audit(EmploymentServicePO event) {
        event.setUpdateBy(SecurityUtils.getUserId());
        event.setUpdateTime(LocalDateTime.now());
    }
}

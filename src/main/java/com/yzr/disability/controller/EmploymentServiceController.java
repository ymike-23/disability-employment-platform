package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.EmploymentServiceCompleteDTO;
import com.yzr.disability.entity.dto.EmploymentMatchDTO;
import com.yzr.disability.entity.dto.EmploymentServiceQueryDTO;
import com.yzr.disability.entity.dto.EmploymentServiceSaveDTO;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.vo.EmploymentPersonVO;
import com.yzr.disability.entity.vo.EmploymentMatchVO;
import com.yzr.disability.entity.vo.EmploymentServiceVO;
import com.yzr.disability.entity.vo.JobVO;
import com.yzr.disability.service.EmploymentService;
import com.yzr.disability.service.EmploymentMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 就业服务模块：服务事件办理、人员历史和人工候选岗位接口。 */
@Tag(name = "就业服务", description = "一次应聘一个批次，一次业务事件一条记录；ADMIN全局，BUSINESS按真实人员网格授权")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/employment-service")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class EmploymentServiceController {

    @Autowired
    private EmploymentService employmentService;

    @Autowired
    private EmploymentMatchService employmentMatchService;

    @Operation(summary = "AI简历匹配岗位", description = "先校验真实简历网格权限，再对最多20个候选岗位脱敏分析；AI不可用时返回人工候选，不写入推荐或就业状态。")
    // AI匹配接口入口：接收简历ID，将匹配分析结果按统一Result格式返回前端。
    @PostMapping("/match")
    public Result<EmploymentMatchVO> match(@Valid @RequestBody EmploymentMatchDTO request) {
        return Result.success(employmentMatchService.match(request));
    }

    @Operation(summary = "分页查询就业服务事件", description = "按人员、岗位、根批次、服务类型和办理状态筛选；默认每页10条，最大100条，分页和总数均按人员网格限定。")
    @GetMapping("/page")
    public Result<PageResult<EmploymentServiceVO>> page(@Valid @ModelAttribute EmploymentServiceQueryDTO query) {
        return Result.success(employmentService.page(query));
    }

    @Operation(summary = "查询就业服务事件详情", description = "返回事件办理状态、计算的批次进度及人员就业状态；权限按事件真实所属人员校验。")
    @GetMapping("/{id}")
    public Result<EmploymentServiceVO> detail(@PathVariable Long id) {
        return Result.success(employmentService.detail(id));
    }

    @Operation(summary = "创建推荐批次或后续事件", description = "首次推荐不传rootServiceId，serviceType必须为1；后续事件关联首次推荐记录且人员、岗位必须相同。"
            + "同人同岗存在未结束批次时不允许重复推荐，后续事件创建为待办；人员就业状态不能由前端指定。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody EmploymentServiceSaveDTO dto) {
        return Result.success(employmentService.create(dto));
    }

    @Operation(summary = "修改待办事件", description = "仅可修改日期、内容及备注，未传或null保持原值；不得改变人员、岗位、批次和服务类型。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmploymentServiceSaveDTO dto) {
        employmentService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "完成服务事件", description = "result使用employment_service_result字典值；校验批次阶段并在同一事务更新人员就业状态。"
            + "只有成功完成入职才标记已就业，一人最多一个在职批次；历史事件保留，不提供普通删除。")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @Valid @RequestBody EmploymentServiceCompleteDTO dto) {
        employmentService.complete(id, dto);
        return Result.success();
    }

    @Operation(summary = "取消待办事件", description = "仅取消该次待办办理，保留记录；不删除已完成入职或离职事实。")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        employmentService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "人员就业状态和历史", description = "按真实人员网格校验权限；就业状态null表示尚未核实，事件按批次关联保留。")
    @GetMapping("/person/{personId}")
    public Result<EmploymentPersonVO> person(@PathVariable Long personId) {
        return Result.success(employmentService.person(personId));
    }

    @Operation(summary = "查询人工推荐候选岗位", description = "复用岗位字段，仅返回启用且未删除企业下未删除、招聘中的岗位；不进行AI匹配。")
    @GetMapping("/candidate-jobs")
    public Result<PageResult<JobVO>> candidateJobs(@Valid @ModelAttribute JobQueryDTO query) {
        return Result.success(employmentService.candidateJobs(query));
    }
}

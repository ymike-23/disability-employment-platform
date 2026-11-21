package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.dto.TrainingPlanSaveDTO;
import com.yzr.disability.entity.vo.TrainingPlanVO;
import com.yzr.disability.service.TrainingPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 培训计划管理接口。
 *
 * <p>管理员负责计划的维护与发布；业务人员只能查看全平台已发布的计划，
 * 不能新增、修改、删除或发布计划（返回 403）。报名人数由培训记录维护，
 * 不能通过本接口直接写入。
 */
@Tag(name = "培训计划管理", description = "培训计划的分页查询、详情、新增、修改、逻辑删除与发布")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/training-plan")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class TrainingPlanController {

    @Autowired
    private TrainingPlanService trainingPlanService;

    @Operation(summary = "分页查询培训计划", description = "支持 planName、category、trainingType、status、publishStatus 筛选；"
            + "各枚举字段均传对应 sys_dict 的启用 dict_value。默认每页10条，最大100条；"
            + "ADMIN 可查询全部计划（含草稿），BUSINESS 只能查询全平台已发布计划。")
    @GetMapping("/page")
    public Result<PageResult<TrainingPlanVO>> page(@Valid @ModelAttribute TrainingPlanQueryDTO query) {
        return Result.success(trainingPlanService.page(query));
    }

    @Operation(summary = "查询培训计划详情", description = "ADMIN 可查询全部计划；"
            + "BUSINESS 只能查询已发布计划，未发布计划返回 403。")
    @GetMapping("/{id}")
    public Result<TrainingPlanVO> detail(@PathVariable Long id) {
        return Result.success(trainingPlanService.detail(id));
    }

    @Operation(summary = "新增培训计划", description = "仅 ADMIN 可用，BUSINESS 返回 403。"
            + "计划名称、开始日期、结束日期和计划人数必填；开始日期不得晚于结束日期，计划人数必须大于0，"
            + "状态字典值默认0；新增计划固定为草稿，报名人数固定为0，两者不能通过本接口指定。")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TrainingPlanSaveDTO dto) {
        return Result.success(trainingPlanService.create(dto));
    }

    @Operation(summary = "修改培训计划", description = "仅 ADMIN 可用，BUSINESS 返回 403。"
            + "计划名称、日期和容量必填；新容量不得小于当前报名人数，其他未传或传null的字段保持原值；"
            + "报名人数与发布状态不可通过本接口修改。")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TrainingPlanSaveDTO dto) {
        trainingPlanService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除培训计划", description = "仅 ADMIN 可用，BUSINESS 返回 403。使用 deleted 逻辑删除。")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingPlanService.delete(id);
        return Result.success();
    }

    @Operation(summary = "发布培训计划", description = "仅 ADMIN 可用，BUSINESS 返回 403。"
            + "发布后该计划对所有业务人员可见并可报名；已取消（status=4）或容量异常的计划不能发布，"
            + "重复发布返回 409。取消发布不使用本接口，仍通过将计划状态置为4（已取消）处理。")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        trainingPlanService.publish(id);
        return Result.success();
    }
}

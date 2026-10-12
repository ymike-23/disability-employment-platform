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

/** 培训计划管理接口；BUSINESS 按真实创建人的当前有效网格访问计划。 */
@Tag(name = "培训计划管理", description = "培训计划的分页查询、详情、新增、修改、逻辑删除")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/training-plan")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class TrainingPlanController {

    @Autowired
    private TrainingPlanService trainingPlanService;

    @Operation(summary = "分页查询培训计划", description = "支持 planName、category、trainingType、status 筛选；"
            + "status 为 0未开始/1进行中/2已结束。默认每页10条，最大100条；"
            + "ADMIN 可查询全部，BUSINESS 仅查询本网格业务人员创建的计划。")
    @GetMapping("/page")
    public Result<PageResult<TrainingPlanVO>> page(@Valid @ModelAttribute TrainingPlanQueryDTO query) {
        return Result.success(trainingPlanService.page(query));
    }

    @Operation(summary = "查询培训计划详情", description = "BUSINESS 按真实创建人的当前有效网格校验权限。")
    @GetMapping("/{id}")
    public Result<TrainingPlanVO> detail(@PathVariable Long id) {
        return Result.success(trainingPlanService.detail(id));
    }

    @Operation(summary = "新增培训计划", description = "计划名称、开始日期、结束日期和计划人数必填；"
            + "开始日期不得晚于结束日期，计划人数必须大于0，已报名人数默认0，状态默认0（未开始）。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TrainingPlanSaveDTO dto) {
        return Result.success(trainingPlanService.create(dto));
    }

    @Operation(summary = "修改培训计划", description = "计划名称、日期和容量必填；"
            + "新容量不得小于当前报名人数，其他未传或传null的字段保持原值。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TrainingPlanSaveDTO dto) {
        trainingPlanService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除培训计划", description = "使用 deleted 逻辑删除。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingPlanService.delete(id);
        return Result.success();
    }
}

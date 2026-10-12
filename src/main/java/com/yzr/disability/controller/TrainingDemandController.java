package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.TrainingDemandQueryDTO;
import com.yzr.disability.entity.dto.TrainingDemandSaveDTO;
import com.yzr.disability.entity.vo.TrainingDemandVO;
import com.yzr.disability.service.TrainingDemandService;
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

/** 培训需求管理接口；业务人员只可访问其唯一有效网格的人员需求。 */
@Tag(name = "培训需求管理", description = "培训需求的分页查询、详情、新增、修改、逻辑删除")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/training-demand")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class TrainingDemandController {

    @Autowired
    private TrainingDemandService trainingDemandService;

    @Operation(summary = "分页查询培训需求", description = "支持 personId、demandType、status 筛选；"
            + "status 为 0待处理/1已安排/2已取消。默认每页10条，最大100条，筛选与网格权限同时生效。")
    @GetMapping("/page")
    public Result<PageResult<TrainingDemandVO>> page(@Valid @ModelAttribute TrainingDemandQueryDTO query) {
        return Result.success(trainingDemandService.page(query));
    }

    @Operation(summary = "查询培训需求详情", description = "根据需求真实所属人员校验网格权限。")
    @GetMapping("/{id}")
    public Result<TrainingDemandVO> detail(@PathVariable Long id) {
        return Result.success(trainingDemandService.detail(id));
    }

    @Operation(summary = "新增培训需求", description = "personId 必须指向可访问的未删除人员；status 默认0（待处理）。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TrainingDemandSaveDTO dto) {
        return Result.success(trainingDemandService.create(dto));
    }

    @Operation(summary = "修改培训需求", description = "personId 必填且不可修改所属人员；"
            + "采用非空字段更新，未传或传null的字段保持原值。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TrainingDemandSaveDTO dto) {
        trainingDemandService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除培训需求", description = "使用 deleted 逻辑删除，不删除所属人员档案。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingDemandService.delete(id);
        return Result.success();
    }
}

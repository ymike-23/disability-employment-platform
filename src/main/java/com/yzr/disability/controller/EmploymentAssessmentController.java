package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.EmploymentAssessmentQueryDTO;
import com.yzr.disability.entity.dto.EmploymentAssessmentSaveDTO;
import com.yzr.disability.entity.vo.EmploymentAssessmentVO;
import com.yzr.disability.service.EmploymentAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "就业能力评估")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/employment-assessment")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class EmploymentAssessmentController {
    @Autowired private EmploymentAssessmentService employmentAssessmentService;

    @Operation(summary = "新增就业能力评估")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody EmploymentAssessmentSaveDTO dto) {
        return Result.success(employmentAssessmentService.create(dto));
    }

    @Operation(summary = "分页查询评估历史")
    @GetMapping("/page")
    public Result<PageResult<EmploymentAssessmentVO>> page(@Valid @ModelAttribute EmploymentAssessmentQueryDTO query) {
        return Result.success(employmentAssessmentService.page(query));
    }

    @Operation(summary = "查询就业能力评估详情")
    @GetMapping("/{id}")
    public Result<EmploymentAssessmentVO> detail(@PathVariable Long id) {
        return Result.success(employmentAssessmentService.detail(id));
    }

    @Operation(summary = "修改就业能力评估")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmploymentAssessmentSaveDTO dto) {
        employmentAssessmentService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "逻辑删除就业能力评估")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employmentAssessmentService.delete(id);
        return Result.success();
    }
}


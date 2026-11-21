package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.ResumeQueryDTO;
import com.yzr.disability.entity.dto.ResumeSaveDTO;
import com.yzr.disability.entity.vo.ResumeVO;
import com.yzr.disability.service.ResumeService;
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

@Tag(name = "简历管理", description = "ADMIN全局管理，BUSINESS仅管理本网格人员的简历")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @Operation(summary = "分页查询简历", description = "支持personId、人员姓名筛选；默认每页10条，最大100条；返回简历及人员基本信息。")
    @GetMapping("/page")
    public Result<PageResult<ResumeVO>> page(@Valid @ModelAttribute ResumeQueryDTO query) {
        return Result.success(resumeService.page(query));
    }

    @Operation(summary = "查询简历详情", description = "按简历真实所属人员校验网格权限，返回简历及人员基本信息。")
    @GetMapping("/{id}")
    public Result<ResumeVO> detail(@PathVariable Long id) {
        return Result.success(resumeService.detail(id));
    }

    @Operation(summary = "新增简历", description = "人员须存在且有权访问；一名人员仅允许一份未删除简历，重复创建返回409。"
            + "未传skillsSummary时，由未删除、已完成的就业类培训名称生成；无培训记录也可创建。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ResumeSaveDTO dto) {
        return Result.success(resumeService.create(dto));
    }

    @Operation(summary = "修改简历", description = "personId必填且不可改变；其他字段未传或为null保留原值，空字符串可清空；人工技能摘要不会被自动覆盖。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ResumeSaveDTO dto) {
        resumeService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "逻辑删除简历", description = "按真实所属人员校验权限；删除后可为该人员重新创建当前简历。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success();
    }
}

package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.CompanyQueryDTO;
import com.yzr.disability.entity.dto.CompanySaveDTO;
import com.yzr.disability.entity.vo.CompanyVO;
import com.yzr.disability.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "企业管理", description = "公共企业资源：ADMIN可读写，BUSINESS只读，不按网格或创建人筛选")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "使用现有Result契约：code=200成功；400参数或字典无效；403无权限；404不存在；409关联冲突"),
        @ApiResponse(responseCode = "401", description = "未登录或JWT已失效，由现有认证体系处理")
})
@RestController
@RequestMapping("/api/company")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Operation(summary = "分页查询企业", description = "ADMIN/BUSINESS可查询。支持企业名称、地区、行业和状态；无筛选时返回全部未删除企业（含停用企业）。"
            + "默认每页10条，最大100条，按创建时间及ID倒序；每条返回完整CompanyVO业务信息，不含deleted及内部审计人员ID。")
    @GetMapping("/page")
    public Result<PageResult<CompanyVO>> page(@Valid @ModelAttribute CompanyQueryDTO query) {
        return Result.success(companyService.page(query));
    }

    @Operation(summary = "查询企业详情", description = "ADMIN/BUSINESS可查询，返回完整企业业务信息；包括停用企业，已删除或不存在返回业务码404。")
    @GetMapping("/{id}")
    public Result<CompanyVO> detail(@Parameter(description = "企业ID，必须大于0", required = true) @PathVariable Long id) {
        return Result.success(companyService.detail(id));
    }

    @Operation(summary = "新增企业", description = "仅ADMIN，其他角色业务码403。企业名称必填，status为整数1正常、0停用，默认1；"
            + "district须为启用字典值。创建人、更新人和时间由服务端维护，成功返回企业ID，参数或字典无效为400。")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CompanySaveDTO dto) {
        return Result.success(companyService.create(dto));
    }

    @Operation(summary = "修改企业", description = "仅ADMIN，其他角色业务码403。企业名称必填，其他未传或为null的字段保持原值；"
            + "审计信息由服务端维护。参数或字典无效为400，不存在或已删除为404，并发变更为409。停用不会删除或隐藏已有岗位。")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "企业ID，必须大于0", required = true) @PathVariable Long id,
                               @Valid @RequestBody CompanySaveDTO dto) {
        companyService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除企业", description = "仅ADMIN，使用逻辑删除；仍有关联的未删除岗位时返回业务码409，不级联删除。"
            + "无权限为403，不存在或已删除为404；参数ID无效为400。")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "企业ID，必须大于0", required = true) @PathVariable Long id) {
        companyService.delete(id);
        return Result.success();
    }
}

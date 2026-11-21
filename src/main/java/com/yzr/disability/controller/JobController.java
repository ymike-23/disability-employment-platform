package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.dto.JobSaveDTO;
import com.yzr.disability.entity.vo.JobDetailVO;
import com.yzr.disability.entity.vo.JobVO;
import com.yzr.disability.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "岗位管理", description = "企业岗位公共资源；ADMIN可查询和维护，BUSINESS只读，不按创建人或人员网格过滤。响应使用现有HTTP状态与Result.code契约。")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/job")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class JobController {

    @Autowired
    private JobService jobService;

    @Operation(summary = "分页查询岗位", description = "ADMIN/BUSINESS可用。支持companyId、jobName、educationRequirement、status筛选，未传筛选条件返回全部未删除岗位。"
            + "默认每页10条、最大100条，按创建时间及ID倒序。返回完整岗位业务信息和companyName，不暴露内部删除标记或审计用户ID；停用企业下岗位仍正常展示。"
            + "Result.code：200成功，400参数或字典无效，403角色无权；未认证沿用认证处理。")
    @GetMapping("/page")
    public Result<PageResult<JobVO>> page(@Valid @ModelAttribute JobQueryDTO query) {
        return Result.success(jobService.page(query));
    }

    @Operation(summary = "查询岗位详情", description = "ADMIN/BUSINESS可用。返回完整岗位信息及企业名称、联系人、电话、地址、地区dict_value和行业；企业停用不影响展示。"
            + "Result.code：200成功，400无效ID，403角色无权，404岗位不存在或已删除。")
    @GetMapping("/{id}")
    public Result<JobDetailVO> detail(@Parameter(description = "岗位ID，必须大于0", required = true) @PathVariable Long id) {
        return Result.success(jobService.detail(id));
    }

    @Operation(summary = "新增岗位", description = "仅ADMIN可用；企业ID、岗位名称必填，所属企业必须存在、未删除且正常启用。招聘人数默认1且必须大于0，岗位状态字符串默认0(招聘中)，仅支持0/1。"
            + "学历使用启用的education.dict_value。审计信息由服务端维护。Result.code：200返回岗位ID，400参数/字典无效，403无写权限，404企业不存在，409企业停用或写入冲突。")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody JobSaveDTO dto) {
        return Result.success(jobService.create(dto));
    }

    @Operation(summary = "修改岗位", description = "仅ADMIN可用；企业ID、岗位名称必填，其他字段未传或为null保留原值。变更所属企业时目标企业必须存在、未删除且正常启用；原企业停用后仍允许普通编辑。"
            + "创建人、更新人及时间不能由前端指定。Result.code：200成功，400参数/字典无效，403无写权限，404岗位或新企业不存在，409目标企业停用或并发冲突。")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "岗位ID，必须大于0", required = true) @PathVariable Long id,
                               @Valid @RequestBody JobSaveDTO dto) {
        jobService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "逻辑删除岗位", description = "仅ADMIN可用，使用MyBatis-Plus逻辑删除，不物理删除企业或岗位。Result.code：200成功，400无效ID，403无写权限，404岗位不存在或已删除，409并发冲突。")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "岗位ID，必须大于0", required = true) @PathVariable Long id) {
        jobService.delete(id);
        return Result.success();
    }
}

package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.DisabledCertQueryDTO;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
import com.yzr.disability.entity.vo.DisabledCertVO;
import com.yzr.disability.service.DisabledCertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "残疾人证件管理", description = "按人员查询、维护当前及历史证件")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/disabled-cert")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
@RequiredArgsConstructor
public class DisabledCertController {

    private final DisabledCertService disabledCertService;

    @Operation(summary = "根据人员查询证件列表", description = "personId 必填，业务人员只能访问本网格人员。"
            + "支持 certNo 精确匹配、certStatus 原值、isCurrent 筛选；历史中文字段原样返回，不自动迁移。"
            + "Result.code：200成功，400参数无效，403无权限，ADMIN查询不存在人员为404。")
    @GetMapping("/list")
    public Result<List<DisabledCertVO>> list(@Valid @ModelAttribute DisabledCertQueryDTO query) {
        return Result.success(disabledCertService.list(query));
    }

    @Operation(summary = "查询人员当前证件", description = "ADMIN可跨网格，BUSINESS仅本网格。"
            + "仅返回 is_current=1 的未删除证件；没有当前证件时 data 为 null，历史中文字段原样返回。"
            + "Result.code：400参数无效，403无权限，ADMIN查询不存在人员为404，多条当前证件为409。")
    @GetMapping("/current")
    public Result<DisabledCertVO> current(@Parameter(description = "人员ID，必须大于0", required = true) @RequestParam Long personId) {
        return Result.success(disabledCertService.current(personId));
    }

    @Operation(summary = "新增证件", description = "新增时 isCurrent 默认 1；设置当前证件会在同一事务内将旧当前证件转为历史。"
            + "certStatus默认cert_status字典值1；certStatus、certType、disabilityType、disabilityLevel须提交对应启用字典的dict_value，"
            + "不能提交中文标签，不扩展字典中的状态。证号最长50。ADMIN可跨网格，BUSINESS仅本网格。"
            + "Result.code：200返回证件ID，400参数/字典/日期/证号冲突，403无权限，ADMIN查询不存在人员为404，写入冲突为409。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DisabledCertSaveDTO dto) {
        return Result.success(disabledCertService.create(dto));
    }

    @Operation(summary = "修改证件", description = "personId 必填且不可改变证件所属人员；采用非空字段更新。"
            + "isCurrent 不传保持原值，设置 1 会将旧当前证件转为历史。四类字典字段只校验本次提交值，必须是启用dict_value；"
            + "未传或为null时保留原值，包括历史中文值，不自动转换。ADMIN可跨网格，BUSINESS按证件真实所属人员网格校验。"
            + "Result.code：200成功，400参数/字典/日期/证号冲突，403无权限，ADMIN查询不存在证件为404，写入冲突为409。")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "证件ID，必须大于0", required = true) @PathVariable Long id,
                               @Valid @RequestBody DisabledCertSaveDTO dto) {
        disabledCertService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除证件", description = "通过 deleted 逻辑删除；删除当前证件后不会自动将历史证件设为当前。"
            + "ADMIN可跨网格，BUSINESS按证件真实所属人员网格校验。"
            + "Result.code：200成功，400无效ID，403无权限，ADMIN查询不存在证件为404，写入冲突为409。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "证件ID，必须大于0", required = true) @PathVariable Long id) {
        disabledCertService.delete(id);
        return Result.success();
    }
}

package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.DisabledCertQueryDTO;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
import com.yzr.disability.entity.vo.DisabledCertVO;
import com.yzr.disability.service.DisabledCertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
            + "支持 certNo 精确匹配、certStatus、isCurrent 和即将到期筛选。")
    @GetMapping("/list")
    public Result<List<DisabledCertVO>> list(@Valid @ModelAttribute DisabledCertQueryDTO query) {
        return Result.success(disabledCertService.list(query));
    }

    @Operation(summary = "查询人员当前证件", description = "仅返回 is_current=1 的未删除证件；没有当前证件时 data 为 null。")
    @GetMapping("/current")
    public Result<DisabledCertVO> current(@RequestParam @Positive Long personId) {
        return Result.success(disabledCertService.current(personId));
    }

    @Operation(summary = "新增证件", description = "新增时 isCurrent 默认 1；设置当前证件会在同一事务内将旧当前证件转为历史。"
            + "certStatus 默认正常，可选正常/换证中/注销/挂失；证号最长 50。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DisabledCertSaveDTO dto) {
        return Result.success(disabledCertService.create(dto));
    }

    @Operation(summary = "修改证件", description = "personId 必填且不可改变证件所属人员；采用非空字段更新。"
            + "isCurrent 不传保持原值，设置 1 会将旧当前证件转为历史。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive Long id, @Valid @RequestBody DisabledCertSaveDTO dto) {
        disabledCertService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除证件", description = "通过 deleted 逻辑删除；删除当前证件后不会自动将历史证件设为当前。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        disabledCertService.delete(id);
        return Result.success();
    }
}

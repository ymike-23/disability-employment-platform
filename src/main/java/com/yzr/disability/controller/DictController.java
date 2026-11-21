package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.vo.DictOptionVO;
import com.yzr.disability.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "字典查询", description = "提供启用字典选项，不提供字典维护接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/dict")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "查询启用的字典选项", description = "仅ADMIN/BUSINESS可用，按sortOrder、id升序返回{value,label}。"
            + "dictType必填，最长50字符；不存在或没有启用项时data为空列表。value用于业务保存，label仅展示。"
            + "沿用Result契约：code=200成功，400参数无效，403无权限；未认证由现有JWT认证返回401。")
    @GetMapping("/list")
    public Result<List<DictOptionVO>> list(
            @Parameter(description = "字典类型，如cert_status、cert_type、disability_type、disability_level", required = true)
            @RequestParam String dictType) {
        return Result.success(dictService.list(dictType));
    }
}

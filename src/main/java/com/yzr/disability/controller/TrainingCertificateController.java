package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.TrainingCertificateQueryDTO;
import com.yzr.disability.entity.vo.TrainingCertificateVO;
import com.yzr.disability.service.TrainingCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "培训电子证书")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/training-certificate")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class TrainingCertificateController {
    @Autowired private TrainingCertificateService trainingCertificateService;

    @Operation(summary = "分页查询培训证书")
    @GetMapping("/page")
    public Result<PageResult<TrainingCertificateVO>> page(@Valid @ModelAttribute TrainingCertificateQueryDTO query) {
        return Result.success(trainingCertificateService.page(query));
    }

    @Operation(summary = "查询培训证书详情")
    @GetMapping("/{id}")
    public Result<TrainingCertificateVO> detail(@PathVariable Long id) {
        return Result.success(trainingCertificateService.detail(id));
    }

    @Operation(summary = "查询人员全部培训证书")
    @GetMapping("/person/{personId}")
    public Result<List<TrainingCertificateVO>> byPerson(@PathVariable Long personId) {
        return Result.success(trainingCertificateService.byPerson(personId));
    }
}


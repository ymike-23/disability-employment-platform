package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "AI为参考适配分析，MANUAL为人工降级；均不自动登记推荐或决定录用")
public record EmploymentMatchVO(String mode, String message, List<Item> items) {
    public record Item(Long jobId, String jobName, String companyName,
                       @Schema(description = "后端加权计算的参考适配分，非录用概率；信息不足为null")
                       BigDecimal totalScore, Scores scores, List<String> reasons, List<String> missingInfo) {
    }

    public record Scores(BigDecimal skills, BigDecimal intention, BigDecimal education, BigDecimal salary) {
    }
}

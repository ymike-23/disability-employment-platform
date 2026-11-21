package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "简历新增/修改参数；修改时人员不可变，其他字段未传或为null保留原值，空字符串可清空。")
public class ResumeSaveDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 255, message = "求职意向长度不能超过 255")
    private String jobIntention;

    @Size(max = 50, message = "期望薪资长度不能超过 50")
    private String expectedSalary;

    @Size(max = 65535, message = "自我评价长度不能超过 65535")
    @Schema(description = "自我评价，UTF-8编码不能超过65535字节")
    private String selfEvaluation;

    @Size(max = 65535, message = "技能摘要长度不能超过 65535")
    @Schema(description = "技能摘要，UTF-8编码不能超过65535字节；保存时合并就业类考核通过且明确确认的技能，不根据培训名称推断技能")
    private String skillsSummary;
}

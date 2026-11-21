package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

/** 不接收operatorId；修改时personId必填且不允许更换，null字段保留原值。 */
@Data
public class EmploymentAssessmentSaveDTO {
    @NotNull(message = "人员ID不能为空") @Positive(message = "人员ID必须大于0")
    private Long personId;
    /** 沿用实库字段约定：1有意愿、2暂时没有、3待确认；新增未传默认3。 */
    @Pattern(regexp = "[123]", message = "就业意愿必须为1、2或3")
    private String employmentIntention;
    @Size(max = 65535)
    private String existingSkills;
    @Size(max = 65535)
    private String basicAbility;
    @Size(max = 65535)
    private String supportNeeds;
    @Size(max = 65535)
    private String developmentDirection;
    @Size(max = 100, message = "培训建议长度不能超过100")
    private String trainingSuggestion;
    @Size(max = 65535)
    private String assessmentSummary;
    private LocalDate assessmentDate;
}


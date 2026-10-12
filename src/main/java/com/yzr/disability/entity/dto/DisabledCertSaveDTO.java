package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 证件新增/修改入参。修改时 personId 必须与证件原所属人员一致。 */
@Data
public class DisabledCertSaveDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 50, message = "证件编号长度不能超过 50")
    private String certNo;

    @Size(max = 20, message = "证件类型长度不能超过 20")
    private String certType;

    private LocalDate issueDate;
    private LocalDate expireDate;

    @Size(max = 100, message = "发证机关长度不能超过 100")
    private String issueOrg;

    @Size(max = 20, message = "残疾类别长度不能超过 20")
    private String disabilityType;

    @Size(max = 20, message = "残疾等级长度不能超过 20")
    private String disabilityLevel;

    @Size(max = 20, message = "证件状态长度不能超过 20")
    private String certStatus;

    /** 新增时不传默认 1；修改时不传保持原值。设为 1 会将旧当前证件转为历史。 */
    @Min(value = 0, message = "当前证件标识只能为 0 或 1")
    @Max(value = 1, message = "当前证件标识只能为 0 或 1")
    private Integer isCurrent;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

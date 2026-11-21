package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 证件新增/修改入参。修改时 personId 必须与证件原所属人员一致。 */
@Data
@Schema(description = "证件登记/编辑参数；personId必填且不可改变归属，其他未传或为null的字段在修改时保留原值")
public class DisabledCertSaveDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    @Schema(description = "所属人员ID，新增和修改均必填；修改时须等于证件真实所属人员", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @Size(max = 50, message = "证件编号长度不能超过 50")
    @Schema(description = "证件编号，同一人员可保留同证号历史证件，其他未删除人员不能占用同一证号", maxLength = 50)
    private String certNo;

    @Size(max = 20, message = "证件类型长度不能超过 20")
    @Schema(description = "启用的cert_type.dict_value，中文显示名通过字典查询", maxLength = 20)
    private String certType;

    @Schema(description = "发证日期；修改只传一个日期时仍校验最终日期区间")
    private LocalDate issueDate;
    @Schema(description = "有效期至，须晚于发证日期；未传或为null时修改保留原值")
    private LocalDate expireDate;

    @Size(max = 100, message = "发证机关长度不能超过 100")
    @Schema(description = "发证机关", maxLength = 100)
    private String issueOrg;

    @Size(max = 20, message = "残疾类别长度不能超过 20")
    @Schema(description = "启用的disability_type.dict_value，不提交中文标签", maxLength = 20)
    private String disabilityType;

    @Size(max = 20, message = "残疾等级长度不能超过 20")
    @Schema(description = "启用的disability_level.dict_value，不提交中文标签", maxLength = 20)
    private String disabilityLevel;

    @Size(max = 20, message = "证件状态长度不能超过 20")
    @Schema(description = "启用的cert_status.dict_value；新增默认1，修改未传或为null保留原值，不自动转换历史中文", maxLength = 20)
    private String certStatus;

    /** 新增时不传默认 1；修改时不传保持原值。设为 1 会将旧当前证件转为历史。 */
    @Min(value = 0, message = "当前证件标识只能为 0 或 1")
    @Max(value = 1, message = "当前证件标识只能为 0 或 1")
    @Schema(description = "1当前、0历史；新增默认1，修改未传或为null保留原值；设为1将原当前证件转为历史", allowableValues = {"0", "1"})
    private Integer isCurrent;

    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注", maxLength = 255)
    private String remark;
}

package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 同一人员的证件列表查询条件，默认包含当前及历史的未删除证件。 */
@Data
@Schema(description = "查询指定人员的当前及历史未删除证件；历史字典字段原值保留")
public class DisabledCertQueryDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    @Schema(description = "所属人员ID，必填；ADMIN可跨网格，BUSINESS只查本网格", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @Size(max = 50, message = "证件编号长度不能超过 50")
    @Schema(description = "证件编号，精确匹配", maxLength = 50)
    private String certNo;

    @Size(max = 20, message = "证件状态长度不能超过 20")
    @Schema(description = "证件状态原值，支持字典编码或历史原值筛选；不会自动转换", maxLength = 20)
    private String certStatus;

    @Min(value = 0, message = "当前证件标识只能为 0 或 1")
    @Max(value = 1, message = "当前证件标识只能为 0 或 1")
    @Schema(description = "1当前、0历史；不传则均返回", allowableValues = {"0", "1"})
    private Integer isCurrent;

    /** 即将到期筛选仅作用于当前证件，排除注销、挂失。 */
    private Boolean expiringSoon;

    @Min(value = 1, message = "预警天数不能小于 1")
    @Max(value = 365, message = "预警天数不能大于 365")
    private Integer expireWithinDays;
}

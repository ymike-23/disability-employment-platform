package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 同一人员的证件列表查询条件，默认包含当前及历史的未删除证件。 */
@Data
public class DisabledCertQueryDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 50, message = "证件编号长度不能超过 50")
    private String certNo;

    @Size(max = 20, message = "证件状态长度不能超过 20")
    private String certStatus;

    @Min(value = 0, message = "当前证件标识只能为 0 或 1")
    @Max(value = 1, message = "当前证件标识只能为 0 或 1")
    private Integer isCurrent;

    /** 即将到期筛选仅作用于当前证件，排除注销、挂失。 */
    private Boolean expiringSoon;

    @Min(value = 1, message = "预警天数不能小于 1")
    @Max(value = 365, message = "预警天数不能大于 365")
    private Integer expireWithinDays;
}

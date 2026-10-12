package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 培训需求新增/修改入参；修改时不允许更换所属人员。 */
@Data
public class TrainingDemandSaveDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 20, message = "需求类型长度不能超过 20")
    private String demandType;

    @Size(max = 500, message = "需求描述长度不能超过 500")
    private String demandDesc;

    /** 新增不传时默认待处理；修改不传时保持原状态。 */
    @Pattern(regexp = "0|1|2", message = "需求状态只能为 0（待处理）、1（已安排）、2（已取消）")
    private String status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 培训需求分页查询条件，与网格数据范围同时生效。 */
@Data
public class TrainingDemandQueryDTO {

    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 20, message = "需求类型长度不能超过 20")
    private String demandType;

    @Pattern(regexp = "0|1|2", message = "需求状态只能为 0（待处理）、1（已安排）、2（已取消）")
    private String status;

    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

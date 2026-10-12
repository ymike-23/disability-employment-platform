package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 培训计划分页查询条件。 */
@Data
public class TrainingPlanQueryDTO {

    @Size(max = 100, message = "计划名称长度不能超过 100")
    private String planName;

    @Pattern(regexp = "1|2", message = "培训大类只能为 1（康复类）、2（就业类）")
    private String category;

    @Size(max = 20, message = "培训类型长度不能超过 20")
    private String trainingType;

    @Pattern(regexp = "0|1|2", message = "计划状态只能为 0（未开始）、1（进行中）、2（已结束）")
    private String status;

    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

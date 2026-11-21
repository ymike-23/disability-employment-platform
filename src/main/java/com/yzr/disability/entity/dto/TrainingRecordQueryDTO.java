package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 培训记录分页查询条件，查询范围由真实所属人员网格确定。 */
@Data
public class TrainingRecordQueryDTO {

    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Positive(message = "培训计划 ID 必须大于 0")
    private Long planId;

    /** training_record_status 的 dict_value。 */
    @Size(max = 20, message = "培训记录状态字典值长度不能超过 20")
    private String status;

    @jakarta.validation.constraints.Pattern(regexp = "[0-3]", message = "考核结果必须为0、1、2或3")
    private String result;

    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

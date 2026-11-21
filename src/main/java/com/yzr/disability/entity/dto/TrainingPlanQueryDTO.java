package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 培训计划分页查询条件。 */
@Data
public class TrainingPlanQueryDTO {

    @Size(max = 100, message = "计划名称长度不能超过 100")
    private String planName;

    /** training_category 字典值。 */
    @Size(max = 20, message = "培训大类字典值长度不能超过 20")
    private String category;

    /** training_type 字典值。 */
    @Size(max = 20, message = "培训类型字典值长度不能超过 20")
    private String trainingType;

    /** training_status 字典值。 */
    @Size(max = 20, message = "计划状态字典值长度不能超过 20")
    private String status;

    /** training_publish_status 字典值，仅管理员可筛选草稿。 */
    @Size(max = 20, message = "发布状态字典值长度不能超过 20")
    private String publishStatus;

    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

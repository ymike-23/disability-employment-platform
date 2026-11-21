package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 培训计划新增/修改入参；创建人由服务端确定。 */
@Data
public class TrainingPlanSaveDTO {

    @NotBlank(message = "计划名称不能为空")
    @Size(max = 100, message = "计划名称长度不能超过 100")
    private String planName;

    /** training_category 字典值，不传中文标签。 */
    @Size(max = 20, message = "培训大类字典值长度不能超过 20")
    private String category;

    /** training_type 字典值，不传中文标签。 */
    @Size(max = 20, message = "培训类型字典值长度不能超过 20")
    private String trainingType;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Size(max = 100, message = "培训地点长度不能超过 100")
    private String location;

    @NotNull(message = "计划人数不能为空")
    @Positive(message = "计划人数必须大于 0")
    private Integer capacity;

    @Size(max = 100, message = "主办单位长度不能超过 100")
    private String organizer;

    /** training_status 字典值；新增不传时默认 0，修改不传时保持原值。 */
    @Size(max = 20, message = "计划状态字典值长度不能超过 20")
    private String status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

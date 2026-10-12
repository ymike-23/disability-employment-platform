package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 培训计划新增/修改入参；创建人由服务端确定。 */
@Data
public class TrainingPlanSaveDTO {

    @NotBlank(message = "计划名称不能为空")
    @Size(max = 100, message = "计划名称长度不能超过 100")
    private String planName;

    @Pattern(regexp = "1|2", message = "培训大类只能为 1（康复类）、2（就业类）")
    private String category;

    @Size(max = 20, message = "培训类型长度不能超过 20")
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

    /** 新增不传时默认 0；修改不传时保留当前报名人数。 */
    @PositiveOrZero(message = "已报名人数不能小于 0")
    private Integer enrolledCount;

    @Size(max = 100, message = "主办单位长度不能超过 100")
    private String organizer;

    /** 新增不传时默认未开始；修改不传时保持原状态。 */
    @Pattern(regexp = "0|1|2", message = "计划状态只能为 0（未开始）、1（进行中）、2（已结束）")
    private String status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

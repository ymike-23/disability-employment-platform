package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 培训记录新增/修改入参；修改时不允许更换所属人员或培训计划。 */
@Data
public class TrainingRecordSaveDTO {

    @NotNull(message = "人员 ID 不能为空")
    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @NotNull(message = "培训计划 ID 不能为空")
    @Positive(message = "培训计划 ID 必须大于 0")
    private Long planId;

    /** training_record_status 的 dict_value；新增不传时默认 1。 */
    @Size(max = 20, message = "培训记录状态字典值长度不能超过 20")
    private String status;

    private LocalDate enrollDate;

    @Digits(integer = 3, fraction = 2, message = "成绩最多允许 3 位整数和 2 位小数")
    private BigDecimal score;

    @Size(max = 50, message = "培训证书编号长度不能超过 50")
    private String certificateNo;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}

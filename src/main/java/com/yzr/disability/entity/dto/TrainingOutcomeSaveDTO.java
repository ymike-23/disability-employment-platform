package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 培训成果独立入参；类别和人员从真实培训记录查询，不接受前端指定。 */
@Data
public class TrainingOutcomeSaveDTO {
    /** 经确认的result编码：0待考核、1通过、2未通过、3未参加，不改变报名status。 */
    @Pattern(regexp = "[0-3]", message = "考核结果必须为0、1、2或3")
    private String result;
    @Digits(integer = 3, fraction = 2, message = "成绩最多允许3位整数和2位小数")
    private BigDecimal score;
    private LocalDate assessmentDate;
    @Size(max = 65535, message = "确认技能过长")
    private String acquiredSkills;
    @Size(max = 65535, message = "康复效果过长")
    private String rehabEffect;
    @Size(max = 65535, message = "改善情况过长")
    private String improvement;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}


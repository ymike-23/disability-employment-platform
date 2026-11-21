package com.yzr.disability.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 培训记录响应对象；状态返回 dict_value，中文展示后续统一由字典模块处理。 */
@Data
public class TrainingRecordVO {

    private Long id;
    private Long personId;
    private Long planId;
    private String status;
    private LocalDate enrollDate;
    private BigDecimal score;
    private String result;
    private LocalDate assessmentDate;
    private String acquiredSkills;
    private String rehabEffect;
    private String improvement;
    private String certificateNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

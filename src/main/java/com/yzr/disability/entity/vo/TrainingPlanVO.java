package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 培训计划响应对象；枚举字段返回 dict_value，中文展示后续统一由字典模块处理。 */
@Data
public class TrainingPlanVO {

    private Long id;
    private String planName;
    private String category;
    private String trainingType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private Integer capacity;
    private Integer enrolledCount;
    private String organizer;
    private String status;
    private String publishStatus;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

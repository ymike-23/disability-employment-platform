package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 培训计划响应对象，不包含逻辑删除等内部字段。 */
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
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** training_plan 表持久化对象，字段对应现有 MySQL 表结构。 */
@Data
@TableName("training_plan")
public class TrainingPlanPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planName;

    /** 1-康复类，2-就业类。 */
    private String category;

    private String trainingType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private Integer capacity;
    private Integer enrolledCount;
    private String organizer;

    /** 0-未开始，1-进行中，2-已结束。 */
    private String status;

    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
}

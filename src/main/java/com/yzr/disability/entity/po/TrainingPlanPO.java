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

    /** sys_dict.training_category 的 dict_value。 */
    private String category;

    /** sys_dict.training_type 的 dict_value。 */
    private String trainingType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private Integer capacity;
    private Integer enrolledCount;
    private String organizer;

    /** sys_dict.training_status 的 dict_value，表示培训业务进度。 */
    private String status;

    /** sys_dict.training_publish_status 的 dict_value，表示是否对外公开。 */
    private String publishStatus;

    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
}

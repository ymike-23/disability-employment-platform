package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 培训记录持久化对象；报名状态status与考核结果result独立存储。 */
@Data
@TableName("training_record")
public class TrainingRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long personId;
    private Long planId;

    /** training_record_status.dict_value；result 列保留原有结业结果。 */
    private String status;

    private LocalDate enrollDate;
    private BigDecimal score;

    /** 0待考核、1通过、2未通过、3未参加；历史null视为尚未登记。 */
    private String result;
    private LocalDate assessmentDate;
    private String acquiredSkills;
    private String rehabEffect;
    private String improvement;

    @TableField("cert_no")
    private String certificateNo;

    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

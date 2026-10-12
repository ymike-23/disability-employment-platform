package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** training_demand 表持久化对象，人员关联及网格权限通过 personId 确定。 */
@Data
@TableName("training_demand")
public class TrainingDemandPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long personId;
    private String demandType;
    private String demandDesc;

    /** 0-待处理，1-已安排，2-已取消。 */
    private String status;

    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
}

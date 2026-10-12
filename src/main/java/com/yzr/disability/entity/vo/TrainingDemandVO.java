package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 培训需求响应对象，不包含 deleted 等内部字段。 */
@Data
public class TrainingDemandVO {

    private Long id;
    private Long personId;
    private String demandType;
    private String demandDesc;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

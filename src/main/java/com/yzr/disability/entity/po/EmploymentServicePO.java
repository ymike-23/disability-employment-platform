package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 就业服务模块：employment_service 表持久化对象。 */
@Data
@TableName("employment_service")
public class EmploymentServicePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long personId;
    private Long jobId;
    private Long rootServiceId;
    private String serviceType;
    private String status;
    private LocalDate serviceDate;
    private String content;
    private String result;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
}

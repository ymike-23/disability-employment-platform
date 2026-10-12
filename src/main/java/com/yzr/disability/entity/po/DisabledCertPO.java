package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** disabled_cert 表持久化对象；证件通过 personId 归属于人员档案。 */
@Data
@TableName("disabled_cert")
public class DisabledCertPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long personId;
    private String certNo;
    private String certType;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private String issueOrg;
    private String disabilityType;
    private String disabilityLevel;
    private String certStatus;

    /** 当前证件：1；历史证件：0。与有效期、证件状态分别管理。 */
    private Integer isCurrent;

    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

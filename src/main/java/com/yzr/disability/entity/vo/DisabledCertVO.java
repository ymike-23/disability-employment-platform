package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 证件响应对象，不暴露逻辑删除标识。 */
@Data
public class DisabledCertVO {

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
    private Integer isCurrent;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

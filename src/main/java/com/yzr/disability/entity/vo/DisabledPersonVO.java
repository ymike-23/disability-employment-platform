package com.yzr.disability.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 残疾人档案返回 VO。
 *
 * <p>不包含 deleted 等内部字段。
 */
@Data
public class DisabledPersonVO {

    private Long id;

    private String name;

    private String gender;

    private String idCard;

    private LocalDate birthDate;

    private String phone;

    private String district;

    /** 所属网格 ID */
    private Long gridId;

    /** 所属网格名称（联查展示） */
    private String gridName;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String disabilityType;

    private String disabilityLevel;

    private String education;

    // ==================== 残疾人证信息（当前证件） ====================

    /** 残疾人证号（与身份证号 idCard 不同） */
    private String certNo;

    /** 发证日期 */
    private LocalDate certIssueDate;

    /** 有效期至 */
    private LocalDate certExpireDate;

    /** 发证机关 */
    private String certIssueOrg;

    /** 证件状态：正常 / 换证中 / 注销 / 挂失，与档案状态 status 无关 */
    private String certStatus;

    /** 状态：0-停用，1-正常 */
    private Integer status;

    private LocalDateTime createTime;
}
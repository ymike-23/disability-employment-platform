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

    /** 状态：0-停用，1-正常 */
    private Integer status;

    private LocalDateTime createTime;
}

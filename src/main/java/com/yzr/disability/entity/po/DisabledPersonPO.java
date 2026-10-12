package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * disabled_person 表持久化对象。
 */
@Data
@TableName("disabled_person")
public class DisabledPersonPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String gender;

    private String idCard;

    private LocalDate birthDate;

    private String phone;

    private String district;

    /** 所属网格，关联 sys_grid.id，可为空 */
    private Long gridId;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String disabilityType;

    private String disabilityLevel;

    private String education;

    /** 状态：0-停用，1-正常 */
    private Integer status;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 创建人，来自当前登录用户 */
    private Long createBy;

    /** 更新人，来自当前登录用户 */
    private Long updateBy;
}

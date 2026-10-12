package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 残疾人档案新增/修改入参。
 *
 * <p>注意：gridId 仅管理员生效；业务人员的 gridId 由后端从当前登录账号解析，
 * 不信任前端传值。
 */
@Data
public class DisabledPersonSaveDTO {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String name;

    private String gender;

    @Size(max = 18, message = "身份证号长度不能超过 18")
    private String idCard;

    private LocalDate birthDate;

    @Size(max = 20, message = "联系电话长度不能超过 20")
    private String phone;

    private String district;

    /** 网格 ID：仅管理员指定，业务人员传值会被忽略 */
    private Long gridId;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String disabilityType;

    private String disabilityLevel;

    private String education;

}

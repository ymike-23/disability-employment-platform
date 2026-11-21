package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "企业新增/修改参数；企业名称必填，修改时其他未传或为null的字段保持原值；审计字段由服务端维护")
public class CompanySaveDTO {

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 100, message = "企业名称长度不能超过100")
    @Schema(description = "企业名称，新增和修改均必填", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    private String companyName;

    @Size(max = 50, message = "联系人长度不能超过50")
    @Schema(description = "联系人，修改未传或为null时保持原值", maxLength = 50)
    private String contactPerson;

    @Size(max = 20, message = "联系电话长度不能超过20")
    @Schema(description = "联系电话，修改未传或为null时保持原值", maxLength = 20)
    private String contactPhone;

    @Size(max = 255, message = "地址长度不能超过255")
    @Schema(description = "地址，修改未传或为null时保持原值", maxLength = 255)
    private String address;

    @Size(max = 20, message = "地区字典值长度不能超过20")
    @Schema(description = "启用的sys_dict.district.dict_value，修改未传或为null时保持原值", maxLength = 20)
    private String district;

    @Size(max = 50, message = "行业长度不能超过50")
    @Schema(description = "行业，修改未传或为null时保持原值", maxLength = 50)
    private String industry;

    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注，修改未传或为null时保持原值", maxLength = 500)
    private String remark;

    @Min(value = 0, message = "企业状态仅允许0或1")
    @Max(value = 1, message = "企业状态仅允许0或1")
    @Schema(description = "整数：1正常、0停用；新增未传或为null时默认1，修改时保持原值", allowableValues = {"0", "1"})
    private Integer status;
}

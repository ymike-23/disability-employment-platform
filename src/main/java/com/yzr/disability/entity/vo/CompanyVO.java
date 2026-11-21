package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "企业完整业务信息；不包含逻辑删除标记和内部审计人员ID")
public class CompanyVO {

    @Schema(description = "企业ID")
    private Long id;
    @Schema(description = "企业名称")
    private String companyName;
    @Schema(description = "联系人")
    private String contactPerson;
    @Schema(description = "联系电话")
    private String contactPhone;
    @Schema(description = "企业地址")
    private String address;
    @Schema(description = "district字典的dict_value，中文名称通过字典展示")
    private String district;
    @Schema(description = "所属行业")
    private String industry;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "企业状态：1正常、0停用", allowableValues = {"0", "1"})
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

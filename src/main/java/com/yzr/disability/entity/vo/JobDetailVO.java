package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "岗位详情：完整岗位业务信息、所属企业名称及联系信息。企业停用不影响查询展示。")
public class JobDetailVO extends JobVO {

    @Schema(description = "所属企业联系人")
    private String companyContactPerson;
    @Schema(description = "所属企业联系电话")
    private String companyContactPhone;
    @Schema(description = "所属企业地址")
    private String companyAddress;
    @Schema(description = "所属企业地区：district.dict_value")
    private String companyDistrict;
    @Schema(description = "所属企业行业")
    private String companyIndustry;
}

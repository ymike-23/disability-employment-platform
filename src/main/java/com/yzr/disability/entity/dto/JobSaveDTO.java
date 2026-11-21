package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "岗位新增/修改参数。companyId、jobName必填；修改时其他字段未传或为null均保留原值。创建人、更新人和时间由服务端维护。")
public class JobSaveDTO {

    @NotNull(message = "企业ID不能为空")
    @Positive(message = "企业ID必须大于0")
    @Schema(description = "所属企业ID；新增或变更企业时，企业必须存在、未删除且正常启用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long companyId;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称长度不能超过100")
    @Schema(description = "岗位名称，最多100字符", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobName;

    @Size(max = 50, message = "薪资长度不能超过50")
    @Schema(description = "薪资原始字符串，最多50字符，例如3000-5000元/月")
    private String salary;

    @Schema(description = "岗位要求，对应MySQL TEXT，UTF-8编码最多65535字节")
    private String requirement;

    @Size(max = 20, message = "学历要求字典值长度不能超过20")
    @Schema(description = "启用的sys_dict education.dict_value，最多20字符")
    private String educationRequirement;

    @Positive(message = "招聘人数必须大于0")
    @Schema(description = "招聘人数，必须大于0；新增默认1，修改未传或null保持原值", defaultValue = "1")
    private Integer headcount;

    @Pattern(regexp = "[01]", message = "岗位状态只能为0或1")
    @Schema(description = "岗位状态字符串：0招聘中，1已结束；新增默认0，修改未传或null保持原值", allowableValues = {"0", "1"}, defaultValue = "0")
    private String status;

    @Size(max = 500, message = "备注长度不能超过500")
    @Schema(description = "备注，最多500字符")
    private String remark;
}

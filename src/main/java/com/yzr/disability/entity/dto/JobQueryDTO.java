package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "岗位分页条件；不传筛选条件时查询全部未删除岗位，包含停用企业下的岗位。")
public class JobQueryDTO {

    @Positive(message = "企业ID必须大于0")
    @Schema(description = "按所属企业ID精确筛选")
    private Long companyId;

    @Size(max = 100, message = "岗位名称长度不能超过100")
    @Schema(description = "岗位名称模糊筛选，最多100字符")
    private String jobName;

    @Size(max = 20, message = "学历要求字典值长度不能超过20")
    @Schema(description = "启用的education.dict_value，精确筛选")
    private String educationRequirement;

    @Pattern(regexp = "[01]", message = "岗位状态只能为0或1")
    @Schema(description = "0招聘中，1已结束", allowableValues = {"0", "1"})
    private String status;

    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码，从1开始", defaultValue = "1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页条数，默认10，最大100", defaultValue = "10")
    private Long size = 10L;
}

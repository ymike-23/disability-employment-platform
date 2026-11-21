package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "企业分页查询条件；不传筛选条件时查询所有未删除企业，含停用企业")
public class CompanyQueryDTO {

    @Size(max = 100, message = "企业名称长度不能超过100")
    @Schema(description = "企业名称，模糊查询", maxLength = 100)
    private String companyName;

    @Size(max = 20, message = "地区字典值长度不能超过20")
    @Schema(description = "启用的district字典值，精确查询", maxLength = 20)
    private String district;

    @Size(max = 50, message = "行业长度不能超过50")
    @Schema(description = "行业，模糊查询", maxLength = 50)
    private String industry;

    @Min(value = 0, message = "企业状态仅允许0或1")
    @Max(value = 1, message = "企业状态仅允许0或1")
    @Schema(description = "整数：1正常、0停用；不传时不限制状态", allowableValues = {"0", "1"})
    private Integer status;

    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码，从1开始", defaultValue = "1", minimum = "1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页条数，最多100", defaultValue = "10", minimum = "1", maximum = "100")
    private Long size = 10L;
}

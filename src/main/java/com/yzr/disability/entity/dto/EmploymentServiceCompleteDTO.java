package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 就业服务模块：事件完成及办理结果参数。 */
@Data
@Schema(description = "完成就业服务事件；result为结果字典值，不是人员就业状态。")
public class EmploymentServiceCompleteDTO {

    @NotBlank(message = "办理结果不能为空")
    @Size(max = 100, message = "办理结果字典值长度不能超过100")
    @Schema(description = "employment_service_result.dict_value：1成功、2失败、3放弃")
    private String result;

    private LocalDate serviceDate;

    @Size(max = 65535, message = "服务内容长度不能超过65535")
    @Schema(description = "服务内容，UTF-8编码不能超过65535字节；未传或null保留原值")
    private String content;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}

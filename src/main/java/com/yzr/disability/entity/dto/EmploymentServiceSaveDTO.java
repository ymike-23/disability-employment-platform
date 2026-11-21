package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 就业服务模块：服务事件新增和待办修改参数。 */
@Data
@Schema(description = "就业服务事件新增/修改参数；人员、岗位、批次及服务类型不可修改，其他字段null保持原值。")
public class EmploymentServiceSaveDTO {

    @NotNull(message = "人员ID不能为空")
    @Positive(message = "人员ID必须大于0")
    private Long personId;

    @NotNull(message = "岗位ID不能为空")
    @Positive(message = "岗位ID必须大于0")
    private Long jobId;

    @Positive(message = "批次根记录ID必须大于0")
    @Schema(description = "首次推荐为空，后续事件指向本批次首次推荐记录；必须与真实人员和岗位一致")
    private Long rootServiceId;

    @NotBlank(message = "服务类型不能为空")
    @Size(max = 20, message = "服务类型字典值长度不能超过20")
    @Schema(description = "service_type.dict_value：1推荐、2联系、3面试、4入职、5回访、6录用、7离职")
    private String serviceType;

    private LocalDate serviceDate;

    @Size(max = 65535, message = "服务内容长度不能超过65535")
    @Schema(description = "服务内容，UTF-8编码不能超过65535字节")
    private String content;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}

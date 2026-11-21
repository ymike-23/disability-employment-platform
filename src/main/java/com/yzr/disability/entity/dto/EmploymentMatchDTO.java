package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "岗位匹配请求；人员归属和匹配资料全部由后端查询")
public class EmploymentMatchDTO {
    @NotNull(message = "简历ID不能为空")
    @Positive(message = "简历ID必须大于0")
    private Long resumeId;
}

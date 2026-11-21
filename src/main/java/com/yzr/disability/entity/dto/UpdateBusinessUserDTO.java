package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBusinessUserDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;
    @NotNull(message = "请选择账号状态")
    @Min(0) @Max(1)
    private Integer status;
    @NotNull(message = "请选择网格")
    @Positive(message = "网格ID必须大于0")
    private Long gridId;
}

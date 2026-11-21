package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessUserQueryDTO {
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;
    @Min(0) @Max(1)
    private Integer status;
    @Min(1)
    private Long current = 1L;
    @Min(1) @Max(100)
    private Long size = 10L;
}

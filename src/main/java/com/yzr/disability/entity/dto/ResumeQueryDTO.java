package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeQueryDTO {

    @Positive(message = "人员 ID 必须大于 0")
    private Long personId;

    @Size(max = 50, message = "姓名长度不能超过 50")
    private String name;

    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

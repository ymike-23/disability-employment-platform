package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrainingCertificateQueryDTO {
    @Positive(message = "人员ID必须大于0")
    private Long personId;
    @Size(max = 50, message = "证书编号长度不能超过50")
    private String certificateNo;
    @Min(1)
    private Long current = 1L;
    @Min(1) @Max(100)
    private Long size = 10L;
}


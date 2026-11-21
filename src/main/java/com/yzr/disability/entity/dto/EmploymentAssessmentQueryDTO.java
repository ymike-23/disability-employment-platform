package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
public class EmploymentAssessmentQueryDTO {
    @Positive(message = "人员ID必须大于0")
    private Long personId;
    @Pattern(regexp = "[123]", message = "就业意愿必须为1、2或3")
    private String employmentIntention;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    @Min(1)
    private Long current = 1L;
    @Min(1) @Max(100)
    private Long size = 10L;
}


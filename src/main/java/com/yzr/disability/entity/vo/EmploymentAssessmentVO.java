package com.yzr.disability.entity.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmploymentAssessmentVO {
    private Long id;
    private Long personId;
    private String employmentIntention;
    private String existingSkills;
    private String basicAbility;
    private String supportNeeds;
    private String developmentDirection;
    private String trainingSuggestion;
    private String assessmentSummary;
    private LocalDate assessmentDate;
    private Long operatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


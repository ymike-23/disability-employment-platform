package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("employment_assessment")
public class EmploymentAssessmentPO {
    @TableId(type = IdType.AUTO)
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
    @TableLogic
    private Integer deleted;
}


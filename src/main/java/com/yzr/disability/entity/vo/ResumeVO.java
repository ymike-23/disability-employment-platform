package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "简历及人员基本信息；枚举返回dict_value，不包含身份证号和逻辑删除标记。")
public class ResumeVO {

    private Long id;
    private Long personId;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String district;
    private Long gridId;
    private String address;
    private String disabilityType;
    private String disabilityLevel;
    private String education;
    private String jobIntention;
    private String expectedSalary;
    private String selfEvaluation;
    private String skillsSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

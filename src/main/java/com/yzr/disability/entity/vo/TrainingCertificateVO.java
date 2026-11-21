package com.yzr.disability.entity.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TrainingCertificateVO {
    private Long id;
    private String certificateNo;
    private String certificateName;
    private LocalDate issueDate;
    private Long personId;
    private Long trainingRecordId;
    private LocalDateTime createTime;
}


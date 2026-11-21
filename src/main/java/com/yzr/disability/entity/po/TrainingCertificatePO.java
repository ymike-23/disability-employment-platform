package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("training_certificate")
public class TrainingCertificatePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String certificateNo;
    private String certificateName;
    private LocalDate issueDate;
    private Long personId;
    private Long trainingRecordId;
    private LocalDateTime createTime;
}


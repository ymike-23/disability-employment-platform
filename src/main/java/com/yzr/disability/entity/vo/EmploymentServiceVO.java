package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 就业服务模块：服务事件、应聘进度与关联资料响应。 */
@Data
@Schema(description = "就业服务事件及关联资料；内部删除标记和审计用户ID不返回。")
public class EmploymentServiceVO {

    private Long id;
    private Long personId;
    private String personName;
    private Long jobId;
    private String jobName;
    private Long companyId;
    private String companyName;
    private Long rootServiceId;
    private String serviceType;

    @Schema(description = "事件办理状态字典值：0待办、1完成、2取消，与批次进度和人员就业状态分别表示")
    private String status;

    private LocalDate serviceDate;
    private String content;

    @Schema(description = "employment_service_result.dict_value：1成功、2失败、3放弃")
    private String result;

    private String remark;

    @Schema(description = "人员employment_status.dict_value：0待就业、1求职中、2已就业、3离职；null表示尚未核实")
    private String employmentStatus;

    @Schema(description = "按本批次有效事件推导：1已推荐、2已联系、3面试通过、4已录用、5已入职、6已离职、7已结束；不存入数据库")
    private String batchStage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

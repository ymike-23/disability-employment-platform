package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 就业服务模块：服务事件分页筛选参数。 */
@Data
public class EmploymentServiceQueryDTO {

    @Positive(message = "人员ID必须大于0")
    private Long personId;

    @Positive(message = "岗位ID必须大于0")
    private Long jobId;

    @Positive(message = "批次根记录ID必须大于0")
    private Long rootServiceId;

    @Size(max = 20, message = "服务类型字典值长度不能超过20")
    private String serviceType;

    @Size(max = 20, message = "办理状态字典值长度不能超过20")
    private String status;

    @Min(value = 1, message = "页码不能小于1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Long size = 10L;
}

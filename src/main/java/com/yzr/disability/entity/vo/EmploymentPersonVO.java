package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 就业服务模块：人员就业状态及服务历史响应。 */
@Data
public class EmploymentPersonVO {

    private Long personId;

    @Schema(description = "employment_status.dict_value；null表示尚未核实，不强制推定待就业")
    private String employmentStatus;

    private List<EmploymentServiceVO> records;
}

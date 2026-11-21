package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "可分配给新业务人员的启用网格")
public class GridOptionVO {

    private Long id;

    private String gridName;

    private String gridCode;

    private String district;

    private String community;
}

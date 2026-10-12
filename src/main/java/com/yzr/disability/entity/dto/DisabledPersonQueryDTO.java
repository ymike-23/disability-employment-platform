package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 残疾人档案分页查询条件。
 */
@Data
public class DisabledPersonQueryDTO {

    /** 姓名（模糊） */
    private String name;

    /** 所属区域 */
    private String district;

    /** 残疾类别 */
    private String disabilityType;

    /** 网格 ID（仅管理员可跨网格指定） */
    private Long gridId;

    /** 当前页码，默认 1 */
    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    /** 每页条数，默认 10，最大 100 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}

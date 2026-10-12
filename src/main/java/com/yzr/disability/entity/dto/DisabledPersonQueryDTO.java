package com.yzr.disability.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

    // ==================== 残疾人证筛选条件 ====================

    /** 残疾人证号（精确匹配） */
    @Size(max = 32, message = "残疾人证号长度不能超过 32")
    private String certNo;

    /** 证件状态筛选：正常 / 换证中 / 注销 / 挂失（非法值返回 400） */
    @Size(max = 10, message = "证件状态长度不能超过 10")
    private String certStatus;

    /**
     * 是否仅查询"即将到期"的档案。
     *
     * <p>仅当传 true 时生效。规则见服务实现：
     * 有效期至在 [今天, 今天+预警天数] 闭区间内，且排除已注销、已挂失证件；
     * 有效期为空的档案不参与预警。
     */
    private Boolean expiringSoon;

    /** 到期预警天数，默认 30 天，允许范围 1~365 */
    @Min(value = 1, message = "预警天数不能小于 1")
    @Max(value = 365, message = "预警天数不能大于 365")
    private Integer expireWithinDays;

    /** 当前页码，默认 1 */
    @Min(value = 1, message = "页码不能小于 1")
    private Long current = 1L;

    /** 每页条数，默认 10，最大 100 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long size = 10L;
}
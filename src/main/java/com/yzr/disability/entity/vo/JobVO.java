package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "岗位完整业务信息及所属企业名称；不包含逻辑删除标记、创建人或更新人ID。")
public class JobVO {

    @Schema(description = "岗位ID")
    private Long id;
    @Schema(description = "所属企业ID")
    private Long companyId;
    @Schema(description = "所属企业名称；历史关联企业已删除时为null，企业停用不影响展示")
    private String companyName;
    @Schema(description = "岗位名称")
    private String jobName;
    @Schema(description = "薪资字符串")
    private String salary;
    @Schema(description = "岗位要求")
    private String requirement;
    @Schema(description = "学历要求：education.dict_value，中文展示由字典机制处理")
    private String educationRequirement;
    @Schema(description = "招聘人数")
    private Integer headcount;
    @Schema(description = "岗位状态：0招聘中，1已结束", allowableValues = {"0", "1"})
    private String status;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

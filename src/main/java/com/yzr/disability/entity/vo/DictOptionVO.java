package com.yzr.disability.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "启用的字典选项；业务提交value，label仅用于展示")
public class DictOptionVO {

    @Schema(description = "sys_dict.dict_value，业务保存值")
    private String value;

    @Schema(description = "sys_dict.dict_label，显示名称")
    private String label;
}

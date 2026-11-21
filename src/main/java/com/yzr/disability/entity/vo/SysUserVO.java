package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户返回 VO。
 *
 * <p>注意：不包含 password 等敏感字段。
 */
@Data
public class SysUserVO {

    private Long id;

    private String username;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 角色编码，如 BUSINESS */
    private String roleCode;

    private Long gridId;

    private String gridName;

    /** 保留真实绑定列表，历史多网格配置不能被静默覆盖。 */
    private List<GridOptionVO> grids = List.of();

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

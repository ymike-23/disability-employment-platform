package com.yzr.disability.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

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

    private LocalDateTime createTime;
}
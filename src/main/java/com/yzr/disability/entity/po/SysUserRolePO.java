package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_user_role 表持久化对象（用户与角色关联）。
 * 该表无 deleted 字段，故不使用逻辑删除。
 */
@Data
@TableName("sys_user_role")
public class SysUserRolePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，关联 sys_user.id */
    private Long userId;

    /** 角色 ID，关联 sys_role.id */
    private Long roleId;

    private LocalDateTime createTime;
}
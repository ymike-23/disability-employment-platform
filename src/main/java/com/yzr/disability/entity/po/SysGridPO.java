package com.yzr.disability.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_grid 表持久化对象。
 */
@Data
@TableName("sys_grid")
public class SysGridPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gridName;

    private String gridCode;

    private String district;

    private String community;

    /** 专责业务人员，关联 sys_user.id */
    private Long gridWorkerId;

    private String gridWorkerName;

    private String description;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
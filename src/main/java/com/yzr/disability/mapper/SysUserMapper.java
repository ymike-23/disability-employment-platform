package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.SysUserPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUserPO> {
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysUserPO selectForUpdate(@Param("id") Long id);
}

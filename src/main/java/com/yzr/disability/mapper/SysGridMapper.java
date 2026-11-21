package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.SysGridPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysGridMapper extends BaseMapper<SysGridPO> {

    @Select("SELECT * FROM sys_grid WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysGridPO selectForUpdate(@Param("id") Long id);
}

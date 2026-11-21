package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.DisabledCertPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DisabledCertMapper extends BaseMapper<DisabledCertPO> {

    @Select("""
            SELECT * FROM disabled_cert
            WHERE id = #{id} AND person_id = #{personId} AND deleted = 0
            FOR UPDATE
            """)
    DisabledCertPO selectForUpdate(@Param("id") Long id, @Param("personId") Long personId);
}

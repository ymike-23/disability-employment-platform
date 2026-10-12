package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.DisabledPersonPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DisabledPersonMapper extends BaseMapper<DisabledPersonPO> {

    /** 在证件写事务内锁定所属档案，串行化同一人员的当前证件切换。 */
    @Select("""
            <script>
            SELECT id, grid_id FROM disabled_person
            WHERE id = #{id} AND deleted = 0
            <if test="gridId != null">AND grid_id = #{gridId}</if>
            FOR UPDATE
            </script>
            """)
    DisabledPersonPO selectAccessibleForUpdate(@Param("id") Long id, @Param("gridId") Long gridId);
}

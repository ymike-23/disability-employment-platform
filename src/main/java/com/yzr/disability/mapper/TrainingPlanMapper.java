package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.TrainingPlanPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrainingPlanMapper extends BaseMapper<TrainingPlanPO> {

    /** 写事务内按真实创建人的网格权限锁定计划，容量校验读取最新报名人数。 */
    @Select("""
            <script>
            SELECT p.* FROM training_plan p
            WHERE p.id = #{id} AND p.deleted = 0
            <if test="gridId != null">
                AND EXISTS (SELECT 1 FROM sys_grid g
                    WHERE g.id = #{gridId} AND g.grid_worker_id = p.create_by
                    AND g.status = 1 AND g.deleted = 0)
            </if>
            FOR UPDATE
            </script>
            """)
    TrainingPlanPO selectAccessibleForUpdate(@Param("id") Long id, @Param("gridId") Long gridId);
}

package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.TrainingPlanPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrainingPlanMapper extends BaseMapper<TrainingPlanPO> {

    /**
     * 写事务内锁定计划行，容量与发布状态校验读取最新数据。
     *
     * <p>计划不再按创建人或网格授权，权限由方法级角色控制，因此此处只按主键加锁。
     */
    @Select("""
            SELECT p.* FROM training_plan p
            WHERE p.id = #{id} AND p.deleted = 0
            FOR UPDATE
            """)
    TrainingPlanPO selectAccessibleForUpdate(@Param("id") Long id);
}

package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.entity.po.TrainingRecordPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TrainingRecordMapper extends BaseMapper<TrainingRecordPO> {

    /** 锁定真实所属关系，避免同一记录重复删除或取消。 */
    @Select("""
            SELECT id, person_id, plan_id, status, enroll_date, score,
                   result, assessment_date, acquired_skills, rehab_effect, improvement,
                   cert_no AS certificate_no, remark, deleted, create_time, update_time
            FROM training_record
            WHERE id = #{id} AND person_id = #{personId} AND plan_id = #{planId} AND deleted = 0
            FOR UPDATE
            """)
    TrainingRecordPO selectForUpdate(@Param("id") Long id, @Param("personId") Long personId,
                                    @Param("planId") Long planId);

    /**
     * 计划已逻辑删除时，开始前且未登记成果的记录仍可释放计数。
     *
     * <p>一并返回日期、类别及发布状态，供报名和成果登记校验使用。
     */
    @Select("""
            SELECT id, plan_name, category, start_date, end_date,
                   capacity, enrolled_count, status, publish_status, deleted FROM training_plan
            WHERE id = #{id} FOR UPDATE
            """)
    TrainingPlanPO selectPlanForUpdate(@Param("id") Long id);

    /**
     * 统计同一人员与计划的报名记录数，含已取消与已逻辑删除的记录。
     *
     * <p>与 uk_person_plan(person_id, plan_id) 唯一约束口径一致，作为并发兜底前的显式校验。
     */
    @Select("""
            SELECT COUNT(*) FROM training_record
            WHERE person_id = #{personId} AND plan_id = #{planId}
            """)
    long countEnrollment(@Param("personId") Long personId, @Param("planId") Long planId);

    /** 报名事务内更新计数，容量条件防止超额；报名不改变计划其他业务字段。 */
    @Update("""
            UPDATE training_plan
            SET enrolled_count = COALESCE(enrolled_count, 0) + 1,
                update_time = CURRENT_TIMESTAMP, update_by = #{userId}
            WHERE id = #{id} AND deleted = 0
              AND COALESCE(enrolled_count, 0) >= 0 AND COALESCE(enrolled_count, 0) < capacity
            """)
    int incrementEnrollment(@Param("id") Long id, @Param("userId") Long userId);

    /** 不将计数减为负数，失败时由服务抛异常回滚记录修改或删除。 */
    @Update("""
            UPDATE training_plan
            SET enrolled_count = enrolled_count - 1,
                update_time = CURRENT_TIMESTAMP, update_by = #{userId}
            WHERE id = #{id} AND enrolled_count > 0
            """)
    int decrementEnrollment(@Param("id") Long id, @Param("userId") Long userId);
}

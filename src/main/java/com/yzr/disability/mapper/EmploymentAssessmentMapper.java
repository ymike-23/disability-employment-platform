package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.EmploymentAssessmentPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EmploymentAssessmentMapper extends BaseMapper<EmploymentAssessmentPO> {
    @Select("""
            SELECT * FROM employment_assessment
            WHERE id = #{id} AND person_id = #{personId} AND deleted = 0 FOR UPDATE
            """)
    EmploymentAssessmentPO selectForUpdate(@Param("id") Long id, @Param("personId") Long personId);
}


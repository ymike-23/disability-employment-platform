package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.entity.dto.ResumeQueryDTO;
import com.yzr.disability.entity.po.ResumePO;
import com.yzr.disability.entity.vo.ResumeVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ResumeMapper extends BaseMapper<ResumePO> {

    @Select("""
            SELECT tr.acquired_skills FROM training_record tr
            JOIN training_plan tp ON tp.id = tr.plan_id
            WHERE tr.person_id = #{personId} AND tr.deleted = 0
              AND tr.status IN ('1','2','3','4') AND tr.result = #{passedResult}
              AND tp.category = #{employmentCategory} AND tr.acquired_skills IS NOT NULL
            ORDER BY tr.assessment_date, tr.id
            """)
    List<String> selectConfirmedEmploymentSkills(@Param("personId") Long personId,
                                               @Param("passedResult") String passedResult,
                                               @Param("employmentCategory") String employmentCategory);

    @Select("""
            <script>
            SELECT r.id, r.person_id, p.name, p.gender, p.birth_date, p.phone,
                   p.district, p.grid_id, p.address, p.disability_type, p.disability_level,
                   p.education, r.job_intention, r.expected_salary, r.self_evaluation,
                   r.skills_summary, r.create_time, r.update_time
            FROM resume r
            JOIN disabled_person p ON p.id = r.person_id AND p.deleted = 0
            WHERE r.deleted = 0
            <if test="gridId != null">AND p.grid_id = #{gridId}</if>
            <if test="query.personId != null">AND r.person_id = #{query.personId}</if>
            <if test="query.name != null and query.name != ''">
              AND p.name LIKE CONCAT('%', #{query.name}, '%')
            </if>
            ORDER BY r.create_time DESC, r.id DESC
            </script>
            """)
    IPage<ResumeVO> selectResumePage(Page<ResumeVO> page, @Param("query") ResumeQueryDTO query,
                                   @Param("gridId") Long gridId);

    @Select("""
            <script>
            SELECT r.id, r.person_id, p.name, p.gender, p.birth_date, p.phone,
                   p.district, p.grid_id, p.address, p.disability_type, p.disability_level,
                   p.education, r.job_intention, r.expected_salary, r.self_evaluation,
                   r.skills_summary, r.create_time, r.update_time
            FROM resume r
            JOIN disabled_person p ON p.id = r.person_id AND p.deleted = 0
            WHERE r.id = #{id} AND r.deleted = 0
            <if test="gridId != null">AND p.grid_id = #{gridId}</if>
            </script>
            """)
    ResumeVO selectResumeDetail(@Param("id") Long id, @Param("gridId") Long gridId);

    @Select("""
            SELECT id FROM resume
            WHERE person_id = #{personId} AND deleted = 0
            FOR UPDATE
            """)
    List<Long> selectActiveIdsForUpdate(@Param("personId") Long personId);

    @Select("""
            SELECT id, person_id, job_intention, expected_salary, self_evaluation,
                   skills_summary, deleted, create_time, update_time
            FROM resume
            WHERE id = #{id} AND person_id = #{personId} AND deleted = 0
            FOR UPDATE
            """)
    ResumePO selectForUpdate(@Param("id") Long id, @Param("personId") Long personId);

    @Select("""
            SELECT DISTINCT tp.plan_name
            FROM training_record tr
            JOIN training_plan tp ON tp.id = tr.plan_id AND tp.deleted = 0
            WHERE tr.person_id = #{personId} AND tr.deleted = 0
              AND tr.status = #{completedStatus} AND tp.category = #{employmentCategory}
            ORDER BY tp.plan_name
            """)
    List<String> selectCompletedEmploymentTrainingNames(@Param("personId") Long personId,
                                                       @Param("completedStatus") String completedStatus,
                                                       @Param("employmentCategory") String employmentCategory);
}

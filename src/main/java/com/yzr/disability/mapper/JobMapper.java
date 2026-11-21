package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.po.JobPO;
import com.yzr.disability.entity.vo.JobDetailVO;
import com.yzr.disability.entity.vo.JobVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 岗位模块：岗位查询、行锁及就业历史引用检查。 */
public interface JobMapper extends BaseMapper<JobPO> {

    @Select("""
            <script>
            SELECT j.id, j.company_id, c.company_name, j.job_name, j.salary, j.requirement,
                   j.education_requirement, j.headcount, j.status, j.remark, j.create_time, j.update_time
            FROM job j
            LEFT JOIN company c ON c.id = j.company_id AND c.deleted = 0
            WHERE j.deleted = 0
            <if test="query.companyId != null">AND j.company_id = #{query.companyId}</if>
            <if test="query.jobName != null and query.jobName != ''">
              AND j.job_name LIKE CONCAT('%', #{query.jobName}, '%')
            </if>
            <if test="query.educationRequirement != null">AND j.education_requirement = #{query.educationRequirement}</if>
            <if test="query.status != null">AND j.status = #{query.status}</if>
            ORDER BY j.create_time DESC, j.id DESC
            </script>
            """)
    IPage<JobVO> selectJobPage(Page<JobVO> page, @Param("query") JobQueryDTO query);

    @Select("""
            SELECT j.id, j.company_id, c.company_name, j.job_name, j.salary, j.requirement,
                   j.education_requirement, j.headcount, j.status, j.remark, j.create_time, j.update_time,
                   c.contact_person AS company_contact_person, c.contact_phone AS company_contact_phone,
                   c.address AS company_address, c.district AS company_district, c.industry AS company_industry
            FROM job j
            LEFT JOIN company c ON c.id = j.company_id AND c.deleted = 0
            WHERE j.id = #{id} AND j.deleted = 0
            """)
    JobDetailVO selectJobDetail(@Param("id") Long id);

    @Select("SELECT * FROM job WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    JobPO selectForUpdate(@Param("id") Long id);

    @Select("SELECT id FROM employment_service WHERE job_id = #{jobId} LIMIT 1 FOR UPDATE")
    Long selectEmploymentReferenceForUpdate(@Param("jobId") Long jobId);

}

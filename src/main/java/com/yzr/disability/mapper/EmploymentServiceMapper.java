package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.entity.dto.EmploymentServiceQueryDTO;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.po.EmploymentServicePO;
import com.yzr.disability.entity.vo.EmploymentServiceVO;
import com.yzr.disability.entity.vo.JobVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 就业服务模块：事件持久化、权限关联查询及人员就业状态更新。 */
public interface EmploymentServiceMapper extends BaseMapper<EmploymentServicePO> {

    @Select("""
            <script>
            SELECT e.id, e.person_id, p.name AS person_name, p.employment_status,
                   e.job_id, j.job_name, j.company_id, c.company_name, e.root_service_id,
                   e.service_type, e.status, e.service_date, e.content, e.result, e.remark,
                   e.create_time, e.update_time
            FROM employment_service e
            JOIN disabled_person p ON p.id = e.person_id AND p.deleted = 0
            LEFT JOIN job j ON j.id = e.job_id
            LEFT JOIN company c ON c.id = j.company_id
            WHERE e.deleted = 0
            <if test="gridId != null">AND p.grid_id = #{gridId}</if>
            <if test="query.personId != null">AND e.person_id = #{query.personId}</if>
            <if test="query.jobId != null">AND e.job_id = #{query.jobId}</if>
            <if test="query.rootServiceId != null">
              AND (e.id = #{query.rootServiceId} OR e.root_service_id = #{query.rootServiceId})
            </if>
            <if test="query.serviceType != null">AND e.service_type = #{query.serviceType}</if>
            <if test="query.status != null">AND e.status = #{query.status}</if>
            ORDER BY e.create_time DESC, e.id DESC
            </script>
            """)
    IPage<EmploymentServiceVO> selectServicePage(Page<EmploymentServiceVO> page,
                                               @Param("query") EmploymentServiceQueryDTO query,
                                               @Param("gridId") Long gridId);

    @Select("""
            <script>
            SELECT e.id, e.person_id, p.name AS person_name, p.employment_status,
                   e.job_id, j.job_name, j.company_id, c.company_name, e.root_service_id,
                   e.service_type, e.status, e.service_date, e.content, e.result, e.remark,
                   e.create_time, e.update_time
            FROM employment_service e
            JOIN disabled_person p ON p.id = e.person_id AND p.deleted = 0
            LEFT JOIN job j ON j.id = e.job_id
            LEFT JOIN company c ON c.id = j.company_id
            WHERE e.id = #{id} AND e.deleted = 0
            <if test="gridId != null">AND p.grid_id = #{gridId}</if>
            </script>
            """)
    EmploymentServiceVO selectServiceDetail(@Param("id") Long id, @Param("gridId") Long gridId);

    @Select("""
            SELECT id, person_id, job_id, root_service_id, service_type, status,
                   service_date, content, result, remark, deleted, create_time,
                   update_time, create_by, update_by
            FROM employment_service
            WHERE person_id = #{personId} AND deleted = 0
            ORDER BY update_time, id
            FOR UPDATE
            """)
    List<EmploymentServicePO> selectPersonForUpdate(@Param("personId") Long personId);

    @Select("""
            <script>
            SELECT e.id, e.person_id, p.name AS person_name, p.employment_status,
                   e.job_id, j.job_name, j.company_id, c.company_name, e.root_service_id,
                   e.service_type, e.status, e.service_date, e.content, e.result, e.remark,
                   e.create_time, e.update_time
            FROM employment_service e
            JOIN disabled_person p ON p.id = e.person_id AND p.deleted = 0
            LEFT JOIN job j ON j.id = e.job_id
            LEFT JOIN company c ON c.id = j.company_id
            WHERE e.person_id = #{personId} AND e.deleted = 0
            <if test="gridId != null">AND p.grid_id = #{gridId}</if>
            ORDER BY e.service_date DESC, e.id DESC
            </script>
            """)
    List<EmploymentServiceVO> selectPersonHistory(@Param("personId") Long personId,
                                                 @Param("gridId") Long gridId);

    @Select("""
            <script>
            SELECT j.id, j.company_id, c.company_name, j.job_name, j.salary, j.requirement,
                   j.education_requirement, j.headcount, j.status, j.remark, j.create_time, j.update_time
            FROM job j
            JOIN company c ON c.id = j.company_id AND c.deleted = 0 AND c.status = 1
            WHERE j.deleted = 0 AND j.status = '0'
            <if test="query.companyId != null">AND j.company_id = #{query.companyId}</if>
            <if test="query.jobName != null and query.jobName != ''">
              AND j.job_name LIKE CONCAT('%', #{query.jobName}, '%')
            </if>
            <if test="query.educationRequirement != null">
              AND j.education_requirement = #{query.educationRequirement}
            </if>
            ORDER BY j.create_time DESC, j.id DESC
            </script>
            """)
    IPage<JobVO> selectCandidateJobs(Page<JobVO> page, @Param("query") JobQueryDTO query);

    @Update("""
            UPDATE disabled_person
            SET employment_status = #{status}, update_time = CURRENT_TIMESTAMP, update_by = #{userId}
            WHERE id = #{personId} AND deleted = 0
            """)
    int updatePersonEmploymentStatus(@Param("personId") Long personId, @Param("status") String status,
                                    @Param("userId") Long userId);
}

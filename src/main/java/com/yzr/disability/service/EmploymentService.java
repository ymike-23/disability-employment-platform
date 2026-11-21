package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.EmploymentServiceCompleteDTO;
import com.yzr.disability.entity.dto.EmploymentServiceQueryDTO;
import com.yzr.disability.entity.dto.EmploymentServiceSaveDTO;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.vo.EmploymentPersonVO;
import com.yzr.disability.entity.vo.EmploymentServiceVO;
import com.yzr.disability.entity.vo.JobVO;

import java.util.Set;

/** 就业服务模块：服务事件办理与人员就业历史业务接口。 */
public interface EmploymentService {

    String TYPE_RECOMMEND = "1";
    String TYPE_CONTACT = "2";
    String TYPE_INTERVIEW = "3";
    String TYPE_ONBOARD = "4";
    String TYPE_FOLLOWUP = "5";
    String TYPE_OFFER = "6";
    String TYPE_LEAVE = "7";

    String STATUS_PENDING = "0";
    String STATUS_COMPLETED = "1";
    String STATUS_CANCELLED = "2";

    String RESULT_SUCCESS = "1";
    String RESULT_FAILURE = "2";
    String RESULT_WITHDRAWN = "3";

    String STAGE_RECOMMENDED = "1";
    String STAGE_CONTACTED = "2";
    String STAGE_INTERVIEWED = "3";
    String STAGE_OFFERED = "4";
    String STAGE_EMPLOYED = "5";
    String STAGE_LEFT = "6";
    String STAGE_CLOSED = "7";

    PageResult<EmploymentServiceVO> page(EmploymentServiceQueryDTO query);

    EmploymentServiceVO detail(Long id);

    Long create(EmploymentServiceSaveDTO dto);

    void update(Long id, EmploymentServiceSaveDTO dto);

    void complete(Long id, EmploymentServiceCompleteDTO dto);

    void cancel(Long id);

    EmploymentPersonVO person(Long personId);

    PageResult<JobVO> candidateJobs(JobQueryDTO query);

    /** 只读复用现有批次进度判断；不获取人员行锁或应聘事务锁。 */
    Set<Long> ongoingJobIds(Long personId);
}

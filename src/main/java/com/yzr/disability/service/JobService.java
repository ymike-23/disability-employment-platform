package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.JobQueryDTO;
import com.yzr.disability.entity.dto.JobSaveDTO;
import com.yzr.disability.entity.vo.JobDetailVO;
import com.yzr.disability.entity.vo.JobVO;

public interface JobService {

    PageResult<JobVO> page(JobQueryDTO query);

    JobDetailVO detail(Long id);

    Long create(JobSaveDTO dto);

    void update(Long id, JobSaveDTO dto);

    void delete(Long id);
}

package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.EmploymentAssessmentQueryDTO;
import com.yzr.disability.entity.dto.EmploymentAssessmentSaveDTO;
import com.yzr.disability.entity.vo.EmploymentAssessmentVO;

public interface EmploymentAssessmentService {
    Long create(EmploymentAssessmentSaveDTO dto);
    PageResult<EmploymentAssessmentVO> page(EmploymentAssessmentQueryDTO query);
    EmploymentAssessmentVO detail(Long id);
    void update(Long id, EmploymentAssessmentSaveDTO dto);
    void delete(Long id);
}


package com.yzr.disability.service;

import com.yzr.disability.entity.dto.EmploymentMatchDTO;
import com.yzr.disability.entity.vo.EmploymentMatchVO;

public interface EmploymentMatchService {
    EmploymentMatchVO match(EmploymentMatchDTO request);
}

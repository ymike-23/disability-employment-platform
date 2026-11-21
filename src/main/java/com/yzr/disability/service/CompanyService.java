package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.CompanyQueryDTO;
import com.yzr.disability.entity.dto.CompanySaveDTO;
import com.yzr.disability.entity.vo.CompanyVO;

public interface CompanyService {

    PageResult<CompanyVO> page(CompanyQueryDTO query);

    CompanyVO detail(Long id);

    Long create(CompanySaveDTO dto);

    void update(Long id, CompanySaveDTO dto);

    void delete(Long id);
}

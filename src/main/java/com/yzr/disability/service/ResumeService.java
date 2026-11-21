package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.ResumeQueryDTO;
import com.yzr.disability.entity.dto.ResumeSaveDTO;
import com.yzr.disability.entity.vo.ResumeVO;

public interface ResumeService {

    PageResult<ResumeVO> page(ResumeQueryDTO query);

    ResumeVO detail(Long id);

    Long create(ResumeSaveDTO dto);

    void update(Long id, ResumeSaveDTO dto);

    void delete(Long id);

    /** 由就业类通过成果调用；加入现有写事务，合并人工技能，必要时创建最小合法简历。 */
    void mergeConfirmedTrainingSkills(Long personId, String acquiredSkills);
}

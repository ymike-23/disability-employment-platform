package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingDemandQueryDTO;
import com.yzr.disability.entity.dto.TrainingDemandSaveDTO;
import com.yzr.disability.entity.vo.TrainingDemandVO;

import java.util.Set;

/** 培训需求服务，沿用 ADMIN/BUSINESS 及人员网格数据范围。 */
public interface TrainingDemandService {

    String STATUS_PENDING = "0";
    String STATUS_ARRANGED = "1";
    String STATUS_CANCELLED = "2";
    Set<String> VALID_STATUSES = Set.of(STATUS_PENDING, STATUS_ARRANGED, STATUS_CANCELLED);

    PageResult<TrainingDemandVO> page(TrainingDemandQueryDTO query);

    TrainingDemandVO detail(Long id);

    Long create(TrainingDemandSaveDTO dto);

    void update(Long id, TrainingDemandSaveDTO dto);

    void delete(Long id);
}

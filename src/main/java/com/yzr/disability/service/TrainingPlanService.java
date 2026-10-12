package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.dto.TrainingPlanSaveDTO;
import com.yzr.disability.entity.vo.TrainingPlanVO;

import java.util.Set;

/** 培训计划服务，状态沿用现有数据库定义。 */
public interface TrainingPlanService {

    String STATUS_NOT_STARTED = "0";
    String STATUS_IN_PROGRESS = "1";
    String STATUS_ENDED = "2";
    Set<String> VALID_STATUSES = Set.of(STATUS_NOT_STARTED, STATUS_IN_PROGRESS, STATUS_ENDED);

    PageResult<TrainingPlanVO> page(TrainingPlanQueryDTO query);

    TrainingPlanVO detail(Long id);

    Long create(TrainingPlanSaveDTO dto);

    void update(Long id, TrainingPlanSaveDTO dto);

    void delete(Long id);
}

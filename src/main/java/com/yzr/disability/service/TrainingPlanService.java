package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingPlanQueryDTO;
import com.yzr.disability.entity.dto.TrainingPlanSaveDTO;
import com.yzr.disability.entity.vo.TrainingPlanVO;

/** 培训计划服务，状态值对应 sys_dict.training_status，合法范围由启用字典项确定。 */
public interface TrainingPlanService {

    String STATUS_NOT_STARTED = "0";
    String STATUS_ENROLLING = "1";
    String STATUS_IN_PROGRESS = "2";
    String STATUS_ENDED = "3";
    String STATUS_CANCELLED = "4";

    /** sys_dict.training_publish_status 的 dict_value。 */
    String PUBLISH_DRAFT = "0";
    String PUBLISH_PUBLISHED = "1";

    PageResult<TrainingPlanVO> page(TrainingPlanQueryDTO query);

    TrainingPlanVO detail(Long id);

    Long create(TrainingPlanSaveDTO dto);

    void update(Long id, TrainingPlanSaveDTO dto);

    void delete(Long id);

    /**
     * 发布培训计划，仅管理员可用；草稿发布后对所有业务人员公开。
     *
     * @param id 培训计划 ID
     */
    void publish(Long id);
}

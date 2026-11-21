package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingRecordQueryDTO;
import com.yzr.disability.entity.dto.TrainingRecordSaveDTO;
import com.yzr.disability.entity.dto.TrainingOutcomeSaveDTO;
import com.yzr.disability.entity.vo.TrainingRecordVO;

/** 培训记录服务，权限关联所属人员，状态校验依赖启用字典项。 */
public interface TrainingRecordService {

    String STATUS_ENROLLED = "1";
    String STATUS_IN_TRAINING = "2";
    String STATUS_COMPLETED = "3";
    String STATUS_NOT_PASSED = "4";
    String STATUS_CANCELLED = "5";
    String RESULT_PENDING = "0";
    String RESULT_PASSED = "1";
    String RESULT_FAILED = "2";
    String RESULT_ABSENT = "3";

    TrainingRecordVO registerOutcome(Long id, TrainingOutcomeSaveDTO dto);

    PageResult<TrainingRecordVO> page(TrainingRecordQueryDTO query);

    TrainingRecordVO detail(Long id);

    Long create(TrainingRecordSaveDTO dto);

    void update(Long id, TrainingRecordSaveDTO dto);

    void delete(Long id);
}

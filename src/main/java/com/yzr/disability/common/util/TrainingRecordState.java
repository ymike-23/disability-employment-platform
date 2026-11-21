package com.yzr.disability.common.util;

import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.service.TrainingPlanService;
import com.yzr.disability.service.TrainingRecordService;
import java.time.LocalDate;

/** 培训结束只推进参训进度，考核结果仍由成果登记维护。 */
public final class TrainingRecordState {
    private TrainingRecordState() {}

    // 与effectiveStatus保持一致，在分页前计算状态，避免筛选总数与页面显示不一致。
    public static final String STATUS_SQL = """
            CASE WHEN training_record.status IN ('1','2') AND EXISTS (
                SELECT 1 FROM training_plan tp
                WHERE tp.id = training_record.plan_id AND tp.deleted = 0
                  AND tp.publish_status = '1' AND tp.status IN ('0','1','2','3')
                  AND tp.start_date IS NOT NULL AND tp.end_date IS NOT NULL
                  AND tp.start_date <= tp.end_date AND tp.end_date < {0}
            ) THEN '3' ELSE training_record.status END
            """;

    public static String effectiveStatus(String status, TrainingPlanPO plan, LocalDate today) {
        if ((TrainingRecordService.STATUS_ENROLLED.equals(status)
                || TrainingRecordService.STATUS_IN_TRAINING.equals(status))
                && plan != null && Integer.valueOf(0).equals(plan.getDeleted())
                && TrainingPlanService.PUBLISH_PUBLISHED.equals(plan.getPublishStatus())
                && TrainingPlanState.validDates(plan) && today.isAfter(plan.getEndDate())
                && TrainingPlanService.STATUS_ENDED.equals(TrainingPlanState.effectiveStatus(plan, today)))
            return TrainingRecordService.STATUS_COMPLETED;
        return status;
    }
}

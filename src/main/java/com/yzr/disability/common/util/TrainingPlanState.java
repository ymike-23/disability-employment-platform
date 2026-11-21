package com.yzr.disability.common.util;

import com.yzr.disability.entity.po.TrainingPlanPO;
import com.yzr.disability.service.TrainingPlanService;
import java.time.LocalDate;
import java.util.Set;

/** 已发布正常计划按服务器日期显示进度，草稿、取消及未知管理状态保持原值。 */
public final class TrainingPlanState {
    private TrainingPlanState() {}
    private static final Set<String> NORMAL = Set.of("0", "1", "2", "3");
    // {0}由查询包装器绑定服务器日期；与effectiveStatus使用相同边界和管理状态规则。
    public static final String STATUS_SQL = """
            CASE WHEN publish_status = '1' AND status IN ('0','1','2','3')
                 AND start_date IS NOT NULL AND end_date IS NOT NULL AND start_date <= end_date
            THEN CASE WHEN {0} < start_date THEN '1'
                      WHEN {0} <= end_date THEN '2' ELSE '3' END
            ELSE status END
            """;

    public static boolean validDates(TrainingPlanPO plan) {
        return plan.getStartDate() != null && plan.getEndDate() != null
                && !plan.getStartDate().isAfter(plan.getEndDate());
    }

    public static String effectiveStatus(TrainingPlanPO plan, LocalDate today) {
        if (!TrainingPlanService.PUBLISH_PUBLISHED.equals(plan.getPublishStatus())
                || !NORMAL.contains(plan.getStatus() == null ? "" : plan.getStatus()) || !validDates(plan))
            return plan.getStatus();
        if (today.isBefore(plan.getStartDate())) return TrainingPlanService.STATUS_ENROLLING;
        return today.isAfter(plan.getEndDate())
                ? TrainingPlanService.STATUS_ENDED : TrainingPlanService.STATUS_IN_PROGRESS;
    }
}


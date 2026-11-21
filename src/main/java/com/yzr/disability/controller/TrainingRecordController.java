package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.TrainingRecordQueryDTO;
import com.yzr.disability.entity.dto.TrainingRecordSaveDTO;
import com.yzr.disability.entity.dto.TrainingOutcomeSaveDTO;
import com.yzr.disability.entity.vo.TrainingRecordVO;
import com.yzr.disability.service.TrainingRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 培训记录管理接口；BUSINESS 只操作本网格人员的记录。 */
@Tag(name = "培训记录管理", description = "培训记录的分页查询、详情、新增、修改、逻辑删除")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/training-record")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class TrainingRecordController {

    @Autowired
    private TrainingRecordService trainingRecordService;

    @Operation(summary = "登记个人培训成果", description = "计划实际结束后登记；就业类通过时在同一事务生成证书并合并确认技能，康复类仅登记康复成果。result为0待考核、1通过、2未通过、3未参加。")
    @PutMapping("/{id}/outcome")
    public Result<TrainingRecordVO> outcome(@PathVariable Long id, @Valid @RequestBody TrainingOutcomeSaveDTO dto) {
        return Result.success(trainingRecordService.registerOutcome(id, dto));
    }

    @Operation(summary = "分页查询培训记录", description = "支持 personId、planId、status 筛选；"
            + "status 传 training_record_status 的启用字典值。默认每页10条，最大100条。")
    @GetMapping("/page")
    public Result<PageResult<TrainingRecordVO>> page(@Valid @ModelAttribute TrainingRecordQueryDTO query) {
        return Result.success(trainingRecordService.page(query));
    }

    @Operation(summary = "查询培训记录详情", description = "根据记录真实 personId 校验网格权限。")
    @GetMapping("/{id}")
    public Result<TrainingRecordVO> detail(@PathVariable Long id) {
        return Result.success(trainingRecordService.detail(id));
    }

    @Operation(summary = "新增培训记录", description = "人员必须存在且在当前用户网格权限内；"
            + "计划必须存在、未逻辑删除、已发布且当前日期早于开始日期，并有剩余名额；"
            + "同一人员与计划只允许一条记录，重复报名返回 409，恢复报名请修改原记录状态。"
            + "在同一事务内校验、新增记录并增加计划报名人数；考核成绩及证书使用成果登记接口维护。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TrainingRecordSaveDTO dto) {
        return Result.success(trainingRecordService.create(dto));
    }

    @Operation(summary = "修改培训记录", description = "personId、planId 必填且不得改变；"
            + "普通取消仅限开始前且尚未登记成果；重复取消返回409。恢复须重新校验报名时间、发布状态及容量。成绩与证书变更使用成果登记接口。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TrainingRecordSaveDTO dto) {
        trainingRecordService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除培训记录", description = "仅允许开始前且尚未登记成果的记录逻辑删除；与有效报名人数恢复在同一事务内完成，保留培训历史。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingRecordService.delete(id);
        return Result.success();
    }
}

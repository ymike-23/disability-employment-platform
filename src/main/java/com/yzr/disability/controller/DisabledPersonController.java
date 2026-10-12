package com.yzr.disability.controller;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.result.Result;
import com.yzr.disability.entity.dto.DisabledPersonQueryDTO;
import com.yzr.disability.entity.dto.DisabledPersonSaveDTO;
import com.yzr.disability.entity.vo.DisabledPersonVO;
import com.yzr.disability.service.DisabledPersonService;
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

/**
 * 残疾人档案管理接口。
 *
 * <p>数据范围：管理员可访问全部；业务人员仅限其唯一有效网格。
 */
@Tag(name = "残疾人档案管理", description = "残疾人档案的分页查询、详情、新增、修改、删除")
@RestController
@RequestMapping("/api/disabled-person")
@PreAuthorize("hasAnyRole('ADMIN','BUSINESS')")
public class DisabledPersonController {

    @Autowired
    private DisabledPersonService disabledPersonService;

    @Operation(
            summary = "分页查询档案",
            description = "支持按姓名（模糊）、区域、残疾类别筛选，并支持证件筛选。<br/>"
                    + "数据范围：管理员返回全部；业务人员仅返回其唯一有效网格内的档案；"
                    + "业务人员未关联网格时返回空分页。<br/>"
                    + "证件筛选参数（均可选，且与数据权限叠加，不会放宽权限）：<br/>"
                    + "• certNo：残疾人证号<b>精确</b>匹配<br/>"
                    + "• certStatus：证件状态，仅可为 正常 / 换证中 / 注销 / 挂失，传其他值返回 400<br/>"
                    + "• expiringSoon=true：仅查即将到期的证件<br/>"
                    + "• expireWithinDays：预警天数，默认 30，范围 1~365<br/>"
                    + "“即将到期”定义为有效期至落在 [今天, 今天+预警天数] 闭区间内；"
                    + "有效期为空、证件状态为注销或挂失的档案不参与预警。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/page")
    public Result<PageResult<DisabledPersonVO>> page(@Valid @ModelAttribute DisabledPersonQueryDTO query) {
        return Result.success(disabledPersonService.page(query));
    }

    @Operation(
            summary = "查询档案详情",
            description = "返回档案基本信息及残疾人证信息"
                    + "（certNo 证号、certIssueDate 发证日期、certExpireDate 有效期至、"
                    + "certIssueOrg 发证机关、certStatus 证件状态）。<br/>"
                    + "历史档案未录入证件时，上述字段返回 null。<br/>"
                    + "业务人员仅可查看本网格档案，越权返回 403。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public Result<DisabledPersonVO> detail(@PathVariable Long id) {
        return Result.success(disabledPersonService.detail(id));
    }

    @Operation(
            summary = "新增档案",
            description = "业务人员的 gridId 由后端从当前登录账号解析，前端传值会被忽略。<br/>"
                    + "身份证号执行查重（数据库无唯一索引，存在并发重复风险）。<br/>"
                    + "残疾人证信息（与身份证号 idCard 是两个不同概念）<b>均选填</b>，历史档案可暂不录入：<br/>"
                    + "• certNo：残疾人证号，最长 32，录入后不允许与其他未删除档案重复<br/>"
                    + "• certIssueDate / certExpireDate：发证日期 / 有效期至；两者都填写时，有效期至必须晚于发证日期<br/>"
                    + "• certIssueOrg：发证机关，最长 100<br/>"
                    + "• certStatus：证件状态，仅可为 正常 / 换证中 / 注销 / 挂失，"
                    + "与档案自身的启用/停用状态(status)无关<br/>"
                    + "残疾人证号同样执行查重；已逻辑删除档案的证号允许复用，故数据库侧未建唯一索引。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DisabledPersonSaveDTO dto) {
        return Result.success(disabledPersonService.create(dto));
    }

    @Operation(
            summary = "修改档案",
            description = "支持维护残疾人证信息，字段含义与校验规则同新增接口；"
                    + "证号查重会排除当前档案自身，因此“保持原证号不变”不会被判为重复。<br/>"
                    + "更新条件包含数据权限（业务人员限定本网格），越权时返回 403 且数据不被修改。<br/>"
                    + "注意：采用非空字段更新策略，传 null 或不传的字段不会被清空。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DisabledPersonSaveDTO dto) {
        disabledPersonService.update(id, dto);
        return Result.success();
    }

    @Operation(
            summary = "删除档案",
            description = "逻辑删除。删除条件包含数据权限（业务人员限定本网格），越权时返回 403 且数据不被删除。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        disabledPersonService.delete(id);
        return Result.success();
    }
}
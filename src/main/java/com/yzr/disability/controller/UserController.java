package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.config.OpenApiConfig;
import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.dto.BusinessUserQueryDTO;
import com.yzr.disability.entity.dto.UpdateBusinessUserDTO;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.vo.GridOptionVO;
import com.yzr.disability.entity.vo.SysUserVO;
import com.yzr.disability.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号管理接口。
 *
 * <p>仅 ADMIN 可调用；创建的账号固定为 BUSINESS 角色，不支持创建管理员账号。
 */
@Tag(name = "账号管理", description = "管理员维护业务人员账号及网格")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(
            summary = "创建业务人员账号",
            description = "仅系统管理员可调用。<br/>"
                    + "请求参数：<br/>"
                    + "• username：用户名（必填，最长 50，不可重复）<br/>"
                    + "• password：初始密码（必填，最低 6 位）<br/>"
                    + "• gridId：真实网格 ID（必填，从 GET /api/admin/grids 选择启用且未分配的网格）<br/>"
                    + "密码使用 BCrypt 加密存储，响应不返回密码。<br/>"
                    + "新建账号固定为 BUSINESS 业务人员角色，账号、角色与网格在同一事务中保存。<br/>"
                    + "Result.code：400 参数或网格无效，403 非管理员或业务角色不可用，409 用户名重复或网格已分配；沿用现有 HTTP 状态契约。"
    )
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @PostMapping("/addusers")
    public Result<SysUserVO> addUsers(@Valid @RequestBody CreateUserDTO dto) {
        return Result.success(userService.createBusinessUser(dto));
    }

    @Operation(summary = "查询可分配网格", description = "仅 ADMIN 可调用。只返回启用、未删除且尚未绑定业务人员的网格，按 ID 升序；无可分配网格时返回空列表。选择后提交仍会重新校验，防止并发占用。")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @GetMapping("/grids")
    public Result<List<GridOptionVO>> grids() {
        return Result.success(userService.listAssignableGrids());
    }

    @Operation(summary = "分页查询业务人员", description = "仅ADMIN，支持用户名、状态筛选；排除管理员及已删除账号，响应不含密码。")
    @GetMapping("/users/page")
    public Result<PageResult<SysUserVO>> page(@Valid BusinessUserQueryDTO query) {
        return Result.success(userService.pageBusinessUsers(query));
    }

    @Operation(summary = "业务人员详情")
    @GetMapping("/users/{id}")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.success(userService.getBusinessUser(id));
    }

    @Operation(summary = "修改业务人员", description = "修改用户名、0停用/1启用状态及网格，密码和角色保持不变；可保留原网格或选择启用且未占用网格。成功后撤销账号会话；不移动人员档案。")
    @PutMapping("/users/{id}")
    public Result<SysUserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateBusinessUserDTO dto) {
        return Result.success(userService.updateBusinessUser(id, dto));
    }

    @Operation(summary = "逻辑删除业务人员", description = "仅ADMIN。释放网格并撤销会话，保留人员档案及历史业务，不能用于删除管理员。")
    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteBusinessUser(id);
        return Result.success();
    }
}

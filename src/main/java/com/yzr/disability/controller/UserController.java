package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.config.OpenApiConfig;
import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.vo.SysUserVO;
import com.yzr.disability.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号管理接口。
 *
 * <p>仅 ADMIN 可调用；创建的账号固定为 BUSINESS 角色，不支持创建管理员账号。
 */
@Tag(name = "账号管理", description = "管理员创建业务人员账号")
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
                    + "密码使用 BCrypt 加密存储，响应不返回密码。<br/>"
                    + "新建账号固定为 BUSINESS 业务人员角色，不支持创建管理员账号。"
    )
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @PostMapping("/addusers")
    public Result<SysUserVO> addUsers(@Valid @RequestBody CreateUserDTO dto) {
        return Result.success(userService.createBusinessUser(dto));
    }
}
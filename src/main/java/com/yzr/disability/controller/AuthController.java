package com.yzr.disability.controller;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.config.OpenApiConfig;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.vo.LoginResponseVO;
import com.yzr.disability.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "登录、退出登录等认证相关接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 登录。业务异常由 GlobalExceptionHandler 统一处理。
     */
    @Operation(
            summary = "用户登录",
            description = "请求参数：<br/>"
                    + "• username：用户名（必填）<br/>"
                    + "• password：密码（必填，明文传输，服务端使用 BCrypt 校验）<br/>"
                    + "校验通过后返回 JWT Token，同时 Token 会保存到 Redis 用于后续会话校验。"
    )
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@RequestBody LoginRequestDTO request) {
        return Result.success(authService.login(request));
    }

    /**
     * 退出登录：删除 Redis 中的 Token。
     * 退出后原 JWT 即使未过期，再次请求也会被拦截（Redis 校验不通过）。
     */
    @Operation(
            summary = "退出登录",
            description = "需要在请求头携带 Bearer Token：<code>Authorization: Bearer {token}</code>。<br/>"
                    + "退出后 Redis 中的 Token 会被删除，原 JWT 即使尚未过期，再次请求也将返回 401。"
    )
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.unauthorized("未登录");
        }

        authService.logout(resolveToken(request));
        return Result.success();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
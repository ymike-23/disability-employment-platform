package com.yzr.disability.service;

import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.vo.LoginResponseVO;

/**
 * 认证业务服务。
 *
 * <p>负责登录、退出登录的业务逻辑，业务错误通过 {@link com.yzr.disability.common.exception.BusinessException} 抛出，
 * 由 GlobalExceptionHandler 统一处理。
 */
public interface AuthService {

    /**
     * 登录：校验用户名密码 → 生成 JWT → 保存 Redis Token
     *
     * @return 登录结果（含 Token）
     */
    LoginResponseVO login(LoginRequestDTO request);

    /**
     * 退出登录：删除 Redis 中的 Token
     *
     * @param token 当前请求的 JWT
     */
    void logout(String token);
}
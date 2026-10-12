package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.config.JwtUtil;
import com.yzr.disability.entity.dto.LoginRequestDTO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.vo.LoginResponseVO;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.service.AuthService;
import com.yzr.disability.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    @Override
    public LoginResponseVO login(LoginRequestDTO request) {
        // 参数校验
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        // 查询用户
        LambdaQueryWrapper<SysUserPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPO::getUsername, request.getUsername().trim());
        SysUserPO user = sysUserMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查状态
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        // BCrypt 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // Redis 保存 Token（TTL 与 JWT 有效期一致）
        tokenService.saveToken(user.getId(), token);

        // sys_user 表无 real_name 字段，此处用 username 填充
        return new LoginResponseVO(token, user.getUsername(), user.getUsername());
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            throw new BusinessException(401, "缺少 Token");
        }

        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new BusinessException(401, "Token 无效");
        }

        if (userId == null) {
            throw new BusinessException(401, "Token 无效");
        }

        tokenService.deleteToken(userId);
    }
}
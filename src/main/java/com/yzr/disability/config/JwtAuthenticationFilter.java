package com.yzr.disability.config;

import com.yzr.disability.common.result.Result;
import com.yzr.disability.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        // 1. 先验证 JWT 本身（签名、过期时间等）
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response, "Token 无效或已过期");
            return;
        }

        // 2. JWT 通过后，再校验 Redis 会话
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            writeUnauthorized(response, "Token 无效");
            return;
        }

        if (userId == null) {
            writeUnauthorized(response, "Token 无效");
            return;
        }

        String redisToken = tokenService.getToken(userId);
        if (redisToken == null) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return;
        }
        // Redis 中 Token 与当前 Token 不一致（如在别处登录或已退出）
        if (!redisToken.equals(token)) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return;
        }

        // 3. 校验通过，重新加载用户信息
        String username = jwtUtil.getUsernameFromToken(token);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            // 用户不存在或已被软删除
            writeUnauthorized(response, "账户不存在或已注销");
            return;
        } catch (Exception e) {
            writeUnauthorized(response, "用户认证失败");
            return;
        }

        // 用户已被禁用（status = 0），禁止继续访问
        if (!userDetails.isEnabled()) {
            writeUnauthorized(response, "账户已被禁用，请联系管理员");
            return;
        }

        // 4. 写入 SecurityContext，继续原有 Spring Security 认证流程
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        objectMapper.writeValue(response.getWriter(), Result.unauthorized(message));
    }
}
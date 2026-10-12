package com.yzr.disability.service;

/**
 * Token 会话存储服务（基于 Redis）。
 *
 * <p>仅负责 Token 的保存、查询、删除与 TTL，
 * 不包含 JWT 生成逻辑与用户业务逻辑。
 */
public interface TokenService {

    /**
     * 保存 Token，TTL 与 JWT 有效期保持一致
     */
    void saveToken(Long userId, String token);

    /**
     * 查询 Token，不存在返回 null
     */
    String getToken(Long userId);

    /**
     * 删除 Token
     */
    void deleteToken(Long userId);
}
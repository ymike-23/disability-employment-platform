package com.yzr.disability.service.impl;

import com.yzr.disability.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenServiceImpl implements TokenService {

    /** Redis Key：login:token:{userId} */
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 与 JWT 有效期保持一致（秒） */
    @Value("${jwt.expiration:86400}")
    private long expiration;

    private String buildKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }

    @Override
    public void saveToken(Long userId, String token) {
        stringRedisTemplate.opsForValue()
                .set(buildKey(userId), token, Duration.ofSeconds(expiration));
    }

    @Override
    public String getToken(Long userId) {
        return stringRedisTemplate.opsForValue().get(buildKey(userId));
    }

    @Override
    public void deleteToken(Long userId) {
        stringRedisTemplate.delete(buildKey(userId));
    }
}
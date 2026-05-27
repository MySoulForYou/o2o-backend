package com.o2o.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    // 默认秘钥（长度建议大于32个字符，确保安全性）
    private static final String SECRET_STRING = "o2o-backend-secure-jwt-secret-key-for-token-generation";
    
    // 生成安全秘钥对象
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));

    /**
     * 生成 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param ttlMs    过期时间（毫秒）
     * @return Token 字符串
     */
    public static String createToken(Long userId, String username, long ttlMs) {
        long nowMs = System.currentTimeMillis();
        Date now = new Date(nowMs);

        // 载荷数据 (Payload)
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        // 构建 JWT
        return Jwts.builder()
                .setClaims(claims)                     // 设置自定义载荷
                .setIssuedAt(now)                      // 设置签发时间
                .setExpiration(new Date(nowMs + ttlMs)) // 设置过期时间
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256) // 签名算法及秘钥
                .compact();
    }

    /**
     * 解析 JWT Token 获取 Payload 数据
     *
     * @param token JWT 令牌
     * @return Claims 载荷
     */
    public static Claims parseToken(String token) {
        JwtParser parser = Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build();
        
        return parser.parseClaimsJws(token).getBody();
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 中提取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }
}

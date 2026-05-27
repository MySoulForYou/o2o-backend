package com.o2o.gateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class JwtUtils {

    // 必须与用户微服务中的秘钥完全一致，否则钢印对不上
    private static final String SECRET_STRING = "o2o-backend-secure-jwt-secret-key-for-token-generation";
    
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));

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

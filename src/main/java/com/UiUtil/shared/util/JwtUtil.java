package com.UiUtil.shared.util;

/**
 * JWT 工具类：基于 JJWT 生成和解析 Token，Token 中携带用户 ID、用户名及角色信息，
 * 默认有效期由配置项 jwt.expiration 控制。
 */
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:outfit_generator_2026}")
    private String secret;

    @Value("${jwt.expire:86400}")
    private Long expire;

    /** 生成 Token，subject 存 userId */
    public String generateToken(String userId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /** 从 Token 中解析 userId */
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /** 验证 Token 有效性（过期/篡改均返回 false） */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
}

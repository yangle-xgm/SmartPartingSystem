package com.smartparking.common.core.utils;

import com.smartparking.common.core.constant.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
public class JwtUtils {

    /** 默认密钥（生产环境应从配置中心读取） */
    private static final String SECRET = "SmartParkingSystemSecretKey2026VeryLongKey!!";
    private static final long EXPIRE_MS = 2 * 60 * 60 * 1000L; // access token 2小时
    private static final long REFRESH_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L; // refresh token 7天

    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Access Token（2小时）
     */
    public static String generateToken(Long userId, String username, Integer role) {
        return buildToken(userId, username, role, null, EXPIRE_MS);
    }

    /**
     * 生成Access Token（2小时，含小区ID）
     */
    public static String generateToken(Long userId, String username, Integer role, Long communityId) {
        return buildToken(userId, username, role, communityId, EXPIRE_MS);
    }

    /**
     * 生成Refresh Token（7天）
     */
    public static String generateRefreshToken(Long userId, String username, Integer role) {
        return buildToken(userId, username, role, null, REFRESH_EXPIRE_MS);
    }

    /**
     * 生成Refresh Token（7天，含小区ID）
     */
    public static String generateRefreshToken(Long userId, String username, Integer role, Long communityId) {
        return buildToken(userId, username, role, communityId, REFRESH_EXPIRE_MS);
    }

    private static String buildToken(Long userId, String username, Integer role, Long communityId, long expireMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.CLAIM_USER_ID, userId);
        claims.put(Constants.CLAIM_USERNAME, username);
        claims.put(Constants.CLAIM_ROLE, role);
        if (communityId != null) {
            claims.put(Constants.CLAIM_COMMUNITY_ID, communityId);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 获取小区ID
     */
    public static Long getCommunityId(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get(Constants.CLAIM_COMMUNITY_ID, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析Token
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(Constants.CLAIM_USER_ID, Long.class);
    }

    /**
     * 获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get(Constants.CLAIM_USERNAME, String.class);
    }

    /**
     * 获取角色
     */
    public static Integer getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get(Constants.CLAIM_ROLE, Integer.class);
    }

    /**
     * 校验Token是否过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}

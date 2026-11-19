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

    /**
     * 内置开发默认密钥：仅用于本地启动/单元测试。
     * 生产必须通过 {@code SMART_PARKING_JWT_SECRET} 或 {@code smart-parking.jwt-secret} 注入，
     * 并由 {@code smart-parking.strict-secrets=true} 强制校验（见 SecuritySecretsConfig）。
     */
    private static final String DEFAULT_SECRET = "SmartParkingSystemSecretKey2026VeryLongKey!!";

    /** 运行期生效的密钥，由 SecuritySecretsConfig 在启动时覆盖 */
    private static volatile String secret = DEFAULT_SECRET;

    private static final long EXPIRE_MS = 2 * 60 * 60 * 1000L; // access token 2小时
    private static final long REFRESH_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L; // refresh token 7天

    private JwtUtils() {
    }

    /**
     * 设置签名密钥（启动期调用一次）。
     * HMAC-SHA256 要求密钥 ≥ 256 bit，过短直接拒绝，避免弱密钥静默生效。
     */
    public static void setSecret(String newSecret) {
        if (newSecret == null || newSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT 密钥长度不足 32 字节，无法满足 HS256 安全强度");
        }
        secret = newSecret;
    }

    /** 是否仍在使用内置默认密钥（供启动自检/健康检查使用） */
    public static boolean usingDefaultSecret() {
        return DEFAULT_SECRET.equals(secret);
    }

    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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

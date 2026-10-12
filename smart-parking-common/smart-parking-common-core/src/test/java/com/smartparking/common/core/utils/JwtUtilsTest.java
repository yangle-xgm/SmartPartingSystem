package com.smartparking.common.core.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类单元测试（纯逻辑，不启 Spring）。
 * 覆盖网关/各微服务共用的令牌生成、解析、声明提取与过期/篡改检测。
 */
class JwtUtilsTest {

    @Test
    void generateAndParse_roundTrip() {
        String token = JwtUtils.generateToken(123L, "alice", 0);
        Claims claims = JwtUtils.parseToken(token);
        assertNotNull(claims);
        assertEquals(123L, JwtUtils.getUserId(token));
        assertEquals("alice", JwtUtils.getUsername(token));
        assertEquals(0, JwtUtils.getRole(token));
    }

    @Test
    void accessToken_expiresInAboutTwoHours() {
        String token = JwtUtils.generateToken(1L, "u", 1);
        long diff = JwtUtils.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
        // 2h = 7_200_000ms，允许 60s 误差
        assertTrue(diff > 7_000_000 && diff < 7_400_000, "access token 应约2小时过期, actual=" + diff);
    }

    @Test
    void refreshToken_expiresInAboutSevenDays() {
        String token = JwtUtils.generateRefreshToken(1L, "u", 1);
        long diff = JwtUtils.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
        assertTrue(diff > 6L * 24 * 3600 * 1000, "refresh token 应约7天过期, actual=" + diff);
    }

    @Test
    void communityId_roundTrip() {
        String token = JwtUtils.generateToken(1L, "u", 1, 99L);
        assertEquals(99L, JwtUtils.getCommunityId(token));
        // 不带 communityId 时返回 null
        assertNull(JwtUtils.getCommunityId(JwtUtils.generateToken(1L, "u", 1)));
    }

    @Test
    void tamperedToken_isInvalid() {
        String token = JwtUtils.generateToken(1L, "u", 1);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThrows(Exception.class, () -> JwtUtils.parseToken(tampered));
        // 解析失败视为过期（网关据此返回 401）
        assertTrue(JwtUtils.isTokenExpired(tampered));
    }

    @Test
    void freshToken_notExpired() {
        assertFalse(JwtUtils.isTokenExpired(JwtUtils.generateToken(1L, "u", 1)));
    }
}

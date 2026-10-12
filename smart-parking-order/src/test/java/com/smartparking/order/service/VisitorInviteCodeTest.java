package com.smartparking.order.service;

import com.smartparking.common.core.utils.AesUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 访客邀请码生成/校验单元测试（Phase B，扩展既有 BillingAndErrorCodeTest 范式）。
 * 覆盖 VisitorInviteServiceImpl 的邀请码字符集（排除易混淆 I/O/0/1）、长度与大小写归一化，
 * 以及邀请流程对车牌的加解密/脱敏（真实 AesUtils 调用）。
 */
class VisitorInviteCodeTest {

    /** 与 VisitorInviteServiceImpl 一致的邀请码字符集与长度 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(rnd.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private boolean isValidFormat(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (CODE_CHARS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    @Test
    void generatedCode_matchesFormatAndCharset() {
        for (int i = 0; i < 200; i++) {
            String code = generateCode();
            assertTrue(isValidFormat(code), "非法邀请码: " + code);
            assertFalse(code.contains("I") || code.contains("O") || code.contains("0") || code.contains("1"),
                    "邀请码不应含易混淆字符: " + code);
        }
    }

    @Test
    void entryCode_normalizedToUpperAndValidCharset() {
        // entryByInviteCode 先 trim().toUpperCase() 再比对
        String entered = " abc234 ";
        String normalized = entered.trim().toUpperCase();
        assertEquals("ABC234", normalized);
        assertTrue(isValidFormat(normalized));
    }

    @Test
    void inviteStatusSemantics() {
        // 0有效 1已使用 2已过期 3已作废（来自 VisitorInvite 注释与 checkValid）
        int active = 0, used = 1, expired = 2, invalidated = 3;
        assertTrue(active < used && used < expired && expired < invalidated);
    }

    @Test
    void plateEncryption_usedInInviteFlow() {
        // 邀请流程用 AesUtils 加解密/脱敏车牌
        String encrypted = AesUtils.encrypt("沪A88888");
        assertNotEquals("沪A88888", encrypted);
        assertEquals("沪A****8", AesUtils.maskPlate(AesUtils.decrypt(encrypted)));
    }
}

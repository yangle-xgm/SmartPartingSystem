package com.smartparking.common.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 工具类单元测试（纯逻辑）。
 * 覆盖手机号/车牌号等敏感字段的加解密与脱敏，被车辆、访客邀请等流程复用。
 */
class AesUtilsTest {

    @Test
    void encryptDecrypt_roundTrip() {
        String plain = "京A12345";
        String enc = AesUtils.encrypt(plain);
        assertNotEquals(plain, enc);
        assertEquals(plain, AesUtils.decrypt(enc));
    }

    @Test
    void encrypt_chinesePlate_roundTrip() {
        String plain = "粤B12345蓝";
        assertEquals(plain, AesUtils.decrypt(AesUtils.encrypt(plain)));
    }

    @Test
    void encrypt_nullOrEmpty_passthrough() {
        assertNull(AesUtils.encrypt(null));
        assertEquals("", AesUtils.encrypt(""));
        assertNull(AesUtils.decrypt(null));
        assertEquals("", AesUtils.decrypt(""));
    }

    @Test
    void maskPhone_standard() {
        assertEquals("138****1234", AesUtils.maskPhone("13800101234"));
    }

    @Test
    void maskPhone_short_unchanged() {
        assertEquals("123", AesUtils.maskPhone("123"));
    }

    @Test
    void maskPlate_standard() {
        // 京A12345 -> 京A****5
        assertEquals("京A****5", AesUtils.maskPlate("京A12345"));
    }

    @Test
    void maskPlate_short_unchanged() {
        assertEquals("AB1", AesUtils.maskPlate("AB1"));
    }
}

package com.smartparking.user.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码哈希单元测试（报告 P0-1 的回归闸门）。
 *
 * <p>历史实现是 {@code "$2a$10$" + MD5(明文)}：无盐、可彩虹表、且伪装成 BCrypt 前缀。
 * 这里锁死三件事：新哈希必须是真 BCrypt（不同次加密结果不同）、旧哈希仍可登录、旧哈希能被识别并升级。
 */
class PasswordCryptoTest {

    private static final String RAW = "123456";

    @Test
    @DisplayName("新密码使用真 BCrypt：每次加盐结果不同，但都能验过")
    void encode_producesSaltedBcrypt() {
        String a = PasswordCrypto.encode(RAW);
        String b = PasswordCrypto.encode(RAW);

        assertTrue(a.startsWith("$2a$"), "应为 BCrypt 格式：" + a);
        assertNotEquals(a, b, "真 BCrypt 每次使用随机盐，两次结果不应相同");
        assertTrue(PasswordCrypto.matches(RAW, a));
        assertTrue(PasswordCrypto.matches(RAW, b));
        assertFalse(PasswordCrypto.matches("wrong", a));
        assertFalse(PasswordCrypto.isLegacy(a), "新格式不应被判为旧格式");
    }

    @Test
    @DisplayName("旧「伪 BCrypt」哈希仍可校验通过（存量账号不被锁死）")
    void matches_legacyMd5Hash() {
        String legacy = "$2a$10$" + DigestUtils.md5DigestAsHex(RAW.getBytes(StandardCharsets.UTF_8));

        assertTrue(PasswordCrypto.isLegacy(legacy), "39 位的旧格式应被识别为待升级");
        assertTrue(PasswordCrypto.matches(RAW, legacy));
        assertFalse(PasswordCrypto.matches("123457", legacy));
    }

    @Test
    @DisplayName("空值/脏数据一律判为不匹配，不抛异常")
    void matches_handlesInvalidInput() {
        assertFalse(PasswordCrypto.matches(null, PasswordCrypto.encode(RAW)));
        assertFalse(PasswordCrypto.matches(RAW, null));
        assertFalse(PasswordCrypto.matches(RAW, "not-a-hash"));
    }
}

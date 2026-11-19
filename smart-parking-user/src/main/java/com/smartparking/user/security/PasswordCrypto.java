package com.smartparking.user.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 密码哈希工具：真 BCrypt（每用户随机盐 + 可调工作因子）。
 *
 * <p>历史数据是「{@code $2a$10$} + 32 位 MD5 hex」的伪 BCrypt（共 39 字符），
 * 盐值固定、可被彩虹表秒破。为兼容存量账号，登录时先按旧算法校验，
 * 校验通过后立即用真 BCrypt 重写该行（透明升级），用户无感知。
 */
public final class PasswordCrypto {

    /** strength=10 与旧前缀里的 10 保持一致的迭代强度，登录耗时相近 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private static final String LEGACY_PREFIX = "$2a$10$";

    /** 7 位前缀 + 32 位 MD5 hex；真 BCrypt 固定 60 位，长度即可区分 */
    private static final int LEGACY_LENGTH = 39;

    private PasswordCrypto() {
    }

    /** 生成真 BCrypt 哈希（60 字符，含随机盐） */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验密码：兼容旧伪 BCrypt，正常走 BCrypt。
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isLegacy(storedPassword)) {
            String legacy = LEGACY_PREFIX
                    + DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
            // 定长比较，避免按字节短路带来的时序差异
            return MessageDigest.isEqual(legacy.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return ENCODER.matches(rawPassword, storedPassword);
        } catch (IllegalArgumentException e) {
            // 非法哈希格式（脏数据）一律视为不匹配，不向调用方抛异常
            return false;
        }
    }

    /** 是否为待升级的旧格式哈希 */
    public static boolean isLegacy(String storedPassword) {
        return storedPassword != null
                && storedPassword.length() == LEGACY_LENGTH
                && storedPassword.startsWith(LEGACY_PREFIX);
    }
}

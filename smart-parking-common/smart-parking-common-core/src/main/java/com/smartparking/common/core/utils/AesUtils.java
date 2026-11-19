package com.smartparking.common.core.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256 加密工具类
 * 用于手机号、车牌号等敏感数据的加密存储
 */
public class AesUtils {

    /** 内置开发默认密钥：生产必须通过 SMART_PARKING_AES_KEY / smart-parking.aes-key 注入 */
    private static final String DEFAULT_AES_KEY = "SmartParkingAES256Key2026Secure!";

    /** 运行期生效的密钥，由 SecuritySecretsConfig 在启动时覆盖 */
    private static volatile String aesKey = DEFAULT_AES_KEY;

    private AesUtils() {
    }

    /**
     * 设置数据密钥（启动期调用一次）。AES-256 要求 32 字节，长度不符直接拒绝。
     */
    public static void setKey(String newKey) {
        if (newKey == null || newKey.getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalArgumentException("AES 密钥必须为 32 字节（AES-256）");
        }
        aesKey = newKey;
    }

    public static boolean usingDefaultKey() {
        return DEFAULT_AES_KEY.equals(aesKey);
    }

    /**
     * 构造密钥。
     *
     * <p>注意：这里刻意保留 ECB 确定性加密 —— 手机号/车牌要做**密文等值查询**
     * （如 {@code WHERE plate_no = encrypt(plate)} 判重与反查），随机 IV 的 CBC/GCM
     * 会让同一明文每次密文不同，现有查询全部失效。彻底改造需新增 blind index 列并回填，
     * 属 schema 级变更，另行排期；本轮只收口密钥来源（报告 P1-4 的硬编码部分）。
     */
    private static SecretKeySpec getSecretKey() {
        try {
            byte[] keyBytes = aesKey.getBytes(StandardCharsets.UTF_8);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("AES密钥初始化失败", e);
        }
    }

    /**
     * 加密
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 安全解密（用于对外返回前的字段还原）
     * 解密失败或原本就是明文时，原样返回，兼容历史明文数据
     */
    public static String decryptSafe(String cipherOrPlain) {
        if (cipherOrPlain == null || cipherOrPlain.isEmpty()) {
            return cipherOrPlain;
        }
        try {
            String plain = decrypt(cipherOrPlain);
            return plain != null ? plain : cipherOrPlain;
        } catch (Exception e) {
            return cipherOrPlain;
        }
    }

    /**
     * 手机号脱敏：138****1234
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 车牌号脱敏：京A****8
     */
    public static String maskPlate(String plate) {
        if (plate == null || plate.length() < 5) {
            return plate;
        }
        return plate.substring(0, 2) + "****" + plate.substring(plate.length() - 1);
    }
}

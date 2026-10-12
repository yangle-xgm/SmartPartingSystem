package com.smartparking.common.core.utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256 加密工具类
 * 用于手机号、车牌号等敏感数据的加密存储
 */
public class AesUtils {

    /** AES密钥（生产环境应从配置中心读取，且定期轮换） */
    private static final String AES_KEY = "SmartParkingAES256Key2026Secure!";

    private static SecretKeySpec getSecretKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            byte[] keyBytes = AES_KEY.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            return keySpec;
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

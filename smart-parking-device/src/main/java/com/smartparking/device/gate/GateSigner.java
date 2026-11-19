package com.smartparking.device.gate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 沙箱道闸指令签名。
 *
 * <p>真实厂商协议几乎都要求「指令 + 设备号 + 序号」带签名，防止重放与伪造开闸
 * （伪造一条 MQTT 报文就抬杆，是设备侧最常见的越权）。沙箱用固定盐的 MD5 演示这套结构，
 * 接真实厂商时把这里换成厂商算法与密钥即可，调用方无需改动。
 */
public final class GateSigner {

    private static final String SALT = "SmartParkingGateSandbox";

    private GateSigner() {
    }

    public static String sign(String cmd, String deviceNo, String seq) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((cmd + "|" + deviceNo + "|" + seq + "|" + SALT)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean verify(String cmd, String deviceNo, String seq, String sign) {
        return sign != null && sign.equals(sign(cmd, deviceNo, seq));
    }
}

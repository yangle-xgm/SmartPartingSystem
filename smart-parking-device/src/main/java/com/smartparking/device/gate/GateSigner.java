package com.smartparking.device.gate;

import com.smartparking.common.core.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 沙箱道闸指令签名。
 *
 * <p>真实厂商协议几乎都要求「指令 + 设备号 + 序号」带签名，防止重放与伪造开闸
 * （伪造一条 MQTT 报文就抬杆，是设备侧最常见的越权）。沙箱用 {@code MD5(cmd|deviceNo|seq|salt)}
 * 演示这套结构，接真实厂商时把这里换成厂商算法与密钥即可，调用方无需改动。
 *
 * <p><b>盐必须可配</b>：早期把盐写死成 {@code private static final String}，结果是接真实厂商
 * 要么改代码、要么让厂商接受我们自创的算法；而且它压根不在 {@code strict-secrets} 视野里，
 * 带着公开盐上线也不会被拦住。现在改由 {@code smart-parking.device.gate-sign-salt}
 * （环境变量 {@code SP_GATE_SIGN_SALT}）注入，默认值仍保留沙箱盐以保证本地/演示开箱可用，
 * 但 strict 模式下使用默认盐会拒绝启动（见 {@code DeviceStrictModeGuard}）。
 *
 * <p>验签用 {@link MessageDigest#isEqual} 做恒定时间比较：{@link String#equals} 会在首个
 * 不同字节处提前返回，理论上可被计时攻击逐位猜出正确签名。
 */
@Slf4j
@Component
public class GateSigner {

    private final String salt;

    public GateSigner(@Value("${smart-parking.device.gate-sign-salt:"
            + Constants.DEFAULT_GATE_SIGN_SALT + "}") String salt) {
        this.salt = StringUtils.hasText(salt) ? salt.trim() : Constants.DEFAULT_GATE_SIGN_SALT;
    }

    /** 当前生效的盐（仅用于启动期自检与日志，不对外暴露）。 */
    public String salt() {
        return salt;
    }

    public boolean isDefaultSalt() {
        return Constants.DEFAULT_GATE_SIGN_SALT.equals(salt);
    }

    public String sign(String cmd, String deviceNo, String seq) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((cmd + "|" + deviceNo + "|" + seq + "|" + salt)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 是 JCA 强制算法，正常不会抛；真抛了也不能放行，返回空串让验签必然失败
            log.error("[道闸签名] 计算失败 cmd={}, deviceNo={}, err={}", cmd, deviceNo, e.getMessage());
            return "";
        }
    }

    public boolean verify(String cmd, String deviceNo, String seq, String sign) {
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        String expected = sign(cmd, deviceNo, seq);
        if (expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}

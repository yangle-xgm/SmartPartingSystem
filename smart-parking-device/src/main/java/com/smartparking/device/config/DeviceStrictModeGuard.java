package com.smartparking.device.config;

import com.smartparking.device.gate.GateSigner;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 设备服务专属的生产就绪校验。
 *
 * <p>公共模块的 {@code SecuritySecretsConfig} 管的是三个全服务通用凭据（JWT / AES / 内部令牌），
 * 道闸签名盐只属于设备域，因此和 {@code PaymentStrictModeGuard} 一样放在本服务内单独校验，
 * 避免写进公共模块后误伤其他服务。
 *
 * <p>为什么必须在 strict 模式拦：
 * <ul>
 *   <li>盐是 {@code MD5(cmd|deviceNo|seq|salt)} 的唯一秘密。默认盐公开写在 {@code Constants} 里，
 *       任何人拿到设备号和序号就能伪造一条 payload 让闸机抬杆 —— 这是设备侧最典型的越权；</li>
 *   <li>而且它替换成本极低（换个环境变量即可），没有理由带着默认值上线。</li>
 * </ul>
 *
 * <p>非 strict 模式只 WARN 不拦：本地演示、答辩环境没有真实硬件，默认盐是开箱可用的前提。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DeviceStrictModeGuard {

    private final GateSigner gateSigner;

    @Value("${smart-parking.strict-secrets:false}")
    private boolean strictSecrets;

    @Value("${smart-parking.device.simulator.enabled:true}")
    private boolean simulatorEnabled;

    @PostConstruct
    public void verify() {
        if (!strictSecrets) {
            if (gateSigner.isDefaultSalt()) {
                log.warn("道闸指令签名盐为内置默认值 —— 仅限本地演示/沙箱，生产必须通过 SP_GATE_SIGN_SALT 注入随机串");
            }
            if (simulatorEnabled) {
                log.warn("沙箱道闸模拟器已开启 —— 它会自动应答所有指令，生产必须置 smart-parking.device.simulator.enabled=false");
            }
            return;
        }

        if (gateSigner.isDefaultSalt()) {
            throw new IllegalStateException(
                    "道闸签名盐仍为内置默认值，生产环境（strict-secrets=true）拒绝启动；"
                            + "请通过 smart-parking.device.gate-sign-salt 或环境变量 SP_GATE_SIGN_SALT 注入随机串");
        }
        if (simulatorEnabled) {
            // 模拟器会自动给任何满足签名的指令回执 OK，等于"下发即成功"，掩盖真实设备离线
            log.warn("沙箱道闸模拟器在生产模式下仍开启，建议置 smart-parking.device.simulator.enabled=false；"
                    + "否则所有编排结果都会被本地模拟器兜住，看不出真实设备是否在线");
        }
        log.info("设备服务生产就绪校验通过：道闸签名盐为自定义值");
    }
}

package com.smartparking.payment.config;

import com.smartparking.common.core.constant.Constants;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 支付服务专属的生产就绪校验。
 *
 * <p>公共模块的 {@code SecuritySecretsConfig} 只覆盖三个全服务通用的凭据（JWT / AES / 内部令牌）。
 * 支付回调密钥与模拟支付开关只存在于 payment，写进公共模块会让其他服务的
 * {@code mock-enabled} 默认值（true）反过来把自己拦在启动之外，因此放在本服务内单独校验。
 *
 * <p>这两项任一漏改，strict 模式形同虚设：
 * <ul>
 *   <li>回调密钥仍是默认值 → 任何人都能按 {@code MD5(payNo + "&" + 公开常量)} 伪造支付成功回调；</li>
 *   <li>模拟支付仍开着 → 界面上的「模拟支付」按钮等于免单入口，且流水不经过任何渠道。</li>
 * </ul>
 */
@Configuration
public class PaymentStrictModeGuard {

    private static final Logger log = LoggerFactory.getLogger(PaymentStrictModeGuard.class);

    @Value("${smart-parking.strict-secrets:false}")
    private boolean strictSecrets;

    @Value("${smart-parking.payment.callback-secret:" + Constants.DEFAULT_CALLBACK_SECRET + "}")
    private String callbackSecret;

    @Value("${smart-parking.payment.mock-enabled:true}")
    private boolean mockEnabled;

    @PostConstruct
    public void verify() {
        boolean defaultCallbackSecret = !StringUtils.hasText(callbackSecret)
                || Constants.DEFAULT_CALLBACK_SECRET.equals(callbackSecret);

        if (!strictSecrets) {
            if (defaultCallbackSecret) {
                log.warn("支付回调密钥等于内置默认值 —— 仅限本地演示，生产必须通过 SP_CALLBACK_SECRET 注入");
            }
            if (mockEnabled) {
                log.warn("模拟支付通道已开启 —— 任何人点「模拟支付」即可免单，生产必须置 smart-parking.payment.mock-enabled=false");
            }
            return;
        }

        // strict-secrets=true：生产就绪的硬约束，任一不满足直接拒绝启动
        if (defaultCallbackSecret) {
            throw new IllegalStateException(
                    "支付回调密钥仍为内置默认值，生产环境（strict-secrets=true）拒绝启动；请通过 smart-parking.payment.callback-secret 注入随机串");
        }
        if (mockEnabled) {
            throw new IllegalStateException(
                    "模拟支付通道未关闭，生产环境（strict-secrets=true）拒绝启动；请置 smart-parking.payment.mock-enabled=false");
        }
        log.info("支付服务生产就绪校验通过：回调密钥为自定义值，模拟支付通道已关闭");
    }
}

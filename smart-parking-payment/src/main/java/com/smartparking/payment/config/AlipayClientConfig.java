package com.smartparking.payment.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端装配。
 *
 * <p>只有 {@code smart-parking.alipay.enabled=true} 时才创建客户端：
 * 关闭状态下不引入任何渠道网络调用，本地演示/CI 不受影响。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlipayClientConfig {

    private final AlipayProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "smart-parking.alipay", name = "enabled", havingValue = "true")
    public AlipayClient alipayClient() {
        DefaultAlipayClient client = new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                normalize(properties.getPrivateKey()),
                "json",
                properties.getCharset(),
                normalize(properties.getAlipayPublicKey()),
                properties.getSignType());
        // 沙箱偶发抖动，超时给足但不至于把请求线程吊死
        client.setConnectTimeout(5000);
        client.setReadTimeout(10000);
        log.info("支付宝客户端已装配：gateway={}, appId={}, signType={}, 配置完整={}",
                properties.getGatewayUrl(), properties.getAppId(), properties.getSignType(), properties.isReady());
        if (!properties.isReady()) {
            log.warn("支付宝 enabled=true 但密钥未配置完整，调用渠道将直接返回「沙箱未配置」");
        }
        return client;
    }

    /**
     * 密钥容错：用户常把 PEM 头尾或换行一起贴进配置。SDK 只认单行 Base64，
     * 这里统一去掉头尾与空白，避免"配了但一直签名失败"。
     */
    public static String normalize(String key) {
        if (key == null) {
            return null;
        }
        return key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }
}

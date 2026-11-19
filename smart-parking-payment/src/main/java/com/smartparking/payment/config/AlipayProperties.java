package com.smartparking.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付宝开放平台（沙箱）配置。
 *
 * <p>沙箱与生产使用同一套 OpenAPI，只是网关 host 不同：
 * <ul>
 *   <li>沙箱：{@code https://openapi-sandbox.dl.alipaydev.com/gateway.do}</li>
 *   <li>生产：{@code https://openapi.alipay.com/gateway.do}</li>
 * </ul>
 * 因此"切生产"只要改 gateway-url + 三把密钥，代码零改动。
 *
 * <p>密钥说明（极易配错，务必区分）：
 * <ul>
 *   <li>{@code private-key}：**应用私钥**（沙箱控制台"接口加签方式"里生成/上传的那对中的私钥），
 *       单行 PKCS8 Base64，不带 -----BEGIN 头尾。</li>
 *   <li>{@code alipay-public-key}：**支付宝公钥**，不是"应用公钥"。用错表现为
 *       {@code 无效的签名 / invalid-signature}，无论怎么改代码都过不了。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "smart-parking.alipay")
public class AlipayProperties {

    /** 总开关：false 时支付宝走本地模拟（原 mock 通道），不加载 SDK 客户端 */
    private boolean enabled = false;

    /** 网关地址，默认沙箱 */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 沙箱 APPID（沙箱控制台顶部显示的 16 位数字，与生产的 APPID 不是同一个） */
    private String appId;

    /** 应用私钥（PKCS8，单行，无头尾） */
    private String privateKey;

    /** 支付宝公钥（不是应用公钥） */
    private String alipayPublicKey;

    /** 签名类型：RSA2(推荐) / RSA */
    private String signType = "RSA2";

    private String charset = "UTF-8";

    /**
     * 异步通知地址。必须是**公网可达**的 http/https 地址，渠道服务器主动 POST 到本机。
     * 本机开发一般收不到（除非内网穿透），因此系统同时提供
     * {@code POST /api/payment/alipay/query} 主动查询结算作为本地闭环手段。
     */
    private String notifyUrl = "http://localhost:8080/api/payment/callback/alipay/notify";

    /** 同步跳转地址（用户付完款后浏览器跳回） */
    private String returnUrl = "http://localhost:3001/#/orders";

    /** 商品标题前缀 */
    private String subjectPrefix = "智慧停车";

    /** 单笔支付超时（分钟），透传给渠道的 timeout_express */
    private int timeoutMinutes = 30;

    /**
     * 配置是否完整可用。缺任意一项都视为不可用：宁可明确报"沙箱未配置"，
     * 也不要拿半套配置去请求渠道（会得到难以排查的 invalid-app-id / invalid-signature）。
     */
    public boolean isReady() {
        return enabled
                && StringUtils.hasText(appId)
                && StringUtils.hasText(privateKey)
                && StringUtils.hasText(alipayPublicKey)
                // 占位符未替换时（仍是 ${...}）视为未配置，避免把占位符当密钥发给渠道
                && !appId.startsWith("${")
                && !privateKey.startsWith("${")
                && !alipayPublicKey.startsWith("${");
    }
}

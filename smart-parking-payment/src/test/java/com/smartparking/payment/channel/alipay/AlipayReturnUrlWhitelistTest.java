package com.smartparking.payment.channel.alipay;

import com.smartparking.payment.config.AlipayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 支付完成回跳地址（return_url / quit_url）白名单的回归测试。
 *
 * <p>背景：配置里的 return-url 是固定的本机地址 {@code http://localhost:3001/#/orders}，
 * 手机 H5 付款后支付宝会把浏览器跳到手机上那个 localhost，指向手机自身，
 * 表现为「付款成功但支付页异常退出 / 页面打不开」。修复方式是前端把当前 origin 传进来，
 * 后端白名单校验后写进签名原文。
 *
 * <p>白名单同时承担防开放跳转的职责：只允许本机与私有网段，
 * 外网域名、非 http(s) 协议、含引号等破坏 JSON 拼接的输入一律退回配置值。
 */
@DisplayName("支付宝回跳地址白名单")
class AlipayReturnUrlWhitelistTest {

    private static final String FALLBACK = "http://localhost:3001/#/orders";

    private AlipaySandboxService service;

    @BeforeEach
    void setUp() {
        AlipayProperties properties = new AlipayProperties();
        properties.setReturnUrl(FALLBACK);
        // resolveReturnUrl 只用 properties，不碰 SDK 客户端，clientProvider 传 null 即可
        service = new AlipaySandboxService(properties, null);
    }

    @Test
    @DisplayName("未传时退回配置值")
    void nullFallsBack() {
        assertEquals(FALLBACK, service.resolveReturnUrl(null));
        assertEquals(FALLBACK, service.resolveReturnUrl("   "));
    }

    @Test
    @DisplayName("热点地址被接受（手机 H5 主路径）")
    void acceptHotspotAddress() {
        assertEquals("http://192.168.137.1:3001/orders",
                service.resolveReturnUrl("http://192.168.137.1:3001/orders"));
    }

    @Test
    @DisplayName("USB 反向映射与校园局域网地址被接受")
    void acceptLoopbackAndLan() {
        assertEquals("http://127.0.0.1:3001/orders/123",
                service.resolveReturnUrl("http://127.0.0.1:3001/orders/123"));
        assertEquals("http://10.168.1.156:3001/orders",
                service.resolveReturnUrl("http://10.168.1.156:3001/orders"));
        assertEquals("https://172.16.0.9:3000/my-orders",
                service.resolveReturnUrl("https://172.16.0.9:3000/my-orders"));
    }

    @Test
    @DisplayName("外网域名退回配置值（防开放跳转）")
    void rejectPublicHost() {
        assertEquals(FALLBACK, service.resolveReturnUrl("http://evil.example.com/orders"));
        assertEquals(FALLBACK, service.resolveReturnUrl("https://192.168.137.1.evil.com/x"));
    }

    @Test
    @DisplayName("非 http(s) 协议被拒绝")
    void rejectNonHttpScheme() {
        assertEquals(FALLBACK, service.resolveReturnUrl("javascript:alert(1)"));
        assertEquals(FALLBACK, service.resolveReturnUrl("file:///C:/Windows/System32"));
        assertEquals(FALLBACK, service.resolveReturnUrl("//192.168.137.1:3001/orders"));
    }

    @Test
    @DisplayName("含引号等破坏签名原文的输入被拒绝")
    void rejectInjection() {
        assertEquals(FALLBACK,
                service.resolveReturnUrl("http://192.168.137.1:3001/orders\",\"subject\":\"x"));
        assertEquals(FALLBACK, service.resolveReturnUrl("http://192.168.137.1:3001/a b"));
    }

    @Test
    @DisplayName("超长地址被拒绝")
    void rejectTooLong() {
        String longUrl = "http://192.168.137.1:3001/" + "a".repeat(300);
        assertEquals(FALLBACK, service.resolveReturnUrl(longUrl));
    }
}

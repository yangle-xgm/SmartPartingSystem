package com.smartparking.common.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D11：审计日志 IP 归一化。
 * 同一台机器经网关透传时，X-Forwarded-For 可能带 IPv6 回环写法，
 * 直接落库会出现 127.0.0.1 与 0:0:0:0:0:0:0:1 两种记录。
 */
class WebRequestUtilsTest {

    @Test
    @DisplayName("IPv6 回环两种写法归一为 127.0.0.1")
    void normalizeLoopback() {
        assertEquals("127.0.0.1", WebRequestUtils.normalize("::1"));
        assertEquals("127.0.0.1", WebRequestUtils.normalize("0:0:0:0:0:0:0:1"));
        assertEquals("127.0.0.1", WebRequestUtils.normalize("[::1]"));
    }

    @Test
    @DisplayName("IPv4 映射地址取回真实 IPv4")
    void normalizeMappedIpv4() {
        assertEquals("127.0.0.1", WebRequestUtils.normalize("::ffff:127.0.0.1"));
        assertEquals("192.168.1.9", WebRequestUtils.normalize("::ffff:192.168.1.9"));
    }

    @Test
    @DisplayName("普通地址原样返回，空值不改变语义")
    void normalizeKeepsOrdinaryIp() {
        assertEquals("10.0.0.8", WebRequestUtils.normalize("10.0.0.8"));
        assertEquals("10.0.0.8", WebRequestUtils.normalize(" 10.0.0.8 "));
        assertNull(WebRequestUtils.normalize(null));
        assertEquals("", WebRequestUtils.normalize(""));
    }
}

package com.smartparking.payment.config;

import com.smartparking.common.core.constant.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产就绪校验：strict-secrets=true 时必须同时满足「回调密钥非默认」与「模拟支付关闭」。
 *
 * <p>这两项任一漏改，strict 模式就是摆设：默认回调密钥可被离线算出签名伪造回调，
 * 开着模拟支付等于给所有人免单。
 */
class PaymentStrictModeGuardTest {

    private PaymentStrictModeGuard guard(boolean strict, String callbackSecret, boolean mockEnabled) {
        PaymentStrictModeGuard guard = new PaymentStrictModeGuard();
        ReflectionTestUtils.setField(guard, "strictSecrets", strict);
        ReflectionTestUtils.setField(guard, "callbackSecret", callbackSecret);
        ReflectionTestUtils.setField(guard, "mockEnabled", mockEnabled);
        return guard;
    }

    @Test
    @DisplayName("开发环境（strict=false）用默认密钥 + 开模拟支付：只告警，不拦启动")
    void devModeOnlyWarns() {
        assertThatCode(() -> guard(false, Constants.DEFAULT_CALLBACK_SECRET, true).verify())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("strict=true 且回调密钥仍是默认值 → 拒绝启动")
    void strictRejectsDefaultCallbackSecret() {
        assertThatThrownBy(() -> guard(true, Constants.DEFAULT_CALLBACK_SECRET, false).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回调密钥");
    }

    @Test
    @DisplayName("strict=true 且模拟支付仍开着 → 拒绝启动")
    void strictRejectsEnabledMock() {
        assertThatThrownBy(() -> guard(true, "a-random-secret", true).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模拟支付");
    }

    @Test
    @DisplayName("strict=true 且密钥为空 → 同样拒绝（空值等于没配）")
    void strictRejectsBlankCallbackSecret() {
        assertThatThrownBy(() -> guard(true, "  ", false).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回调密钥");
    }

    @Test
    @DisplayName("strict=true 且两项都改好 → 允许启动")
    void strictPassesWhenProperlyConfigured() {
        assertThatCode(() -> guard(true, "a-random-secret", false).verify())
                .doesNotThrowAnyException();
    }
}

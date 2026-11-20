package com.smartparking.common.core.config;

import com.smartparking.common.core.constant.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密钥装配的生产就绪校验。
 *
 * <p>重点是内部令牌：它原先根本不在 strict 校验范围内，等于「开了 strict 仍然可以带着
 * 仓库里公开的令牌上线」，任何人直连 8081-8090 就能伪造支付、抬杆、改订单归属。
 */
class SecuritySecretsConfigTest {

    private SecuritySecretsConfig config(boolean strict, String jwt, String aes, String internalToken) {
        SecuritySecretsConfig config = new SecuritySecretsConfig();
        ReflectionTestUtils.setField(config, "strictSecrets", strict);
        ReflectionTestUtils.setField(config, "jwtSecret", jwt);
        ReflectionTestUtils.setField(config, "aesKey", aes);
        ReflectionTestUtils.setField(config, "internalToken", internalToken);
        return config;
    }

    @Test
    @DisplayName("strict=true 且内部令牌仍是默认值 → 拒绝启动")
    void strictRejectsDefaultInternalToken() {
        assertThatThrownBy(() -> config(true, "test-jwt-secret-0123456789abcdefghijklmn", "0123456789abcdef0123456789abcdef", Constants.DEFAULT_INTERNAL_TOKEN).apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("内部接口令牌");
    }

    @Test
    @DisplayName("strict=true 且内部令牌为空 → 同样拒绝")
    void strictRejectsBlankInternalToken() {
        assertThatThrownBy(() -> config(true, "test-jwt-secret-0123456789abcdefghijklmn", "0123456789abcdef0123456789abcdef", " ").apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("内部接口令牌");
    }

    @Test
    @DisplayName("strict=true 且三类凭据都已自定义 → 允许启动")
    void strictPassesWhenAllCustomized() {
        assertThatCode(() -> config(true, "test-jwt-secret-0123456789abcdefghijklmn", "0123456789abcdef0123456789abcdef", "internal-token-y").apply())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("strict=false（开发）用默认令牌 → 只告警，不拦启动")
    void devModeAllowsDefaultInternalToken() {
        assertThatCode(() -> config(false, "test-jwt-secret-0123456789abcdefghijklmn", "0123456789abcdef0123456789abcdef", Constants.DEFAULT_INTERNAL_TOKEN).apply())
                .doesNotThrowAnyException();
    }
}

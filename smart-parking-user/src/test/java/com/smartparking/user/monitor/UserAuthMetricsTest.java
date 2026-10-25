package com.smartparking.user.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthMetricsTest {

    @Test
    void countersRegisterAndAccumulate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserAuthMetrics metrics = new UserAuthMetrics(registry);

        Counter success = registry.find("user_login_success_total").counter();
        Counter fail = registry.find("user_login_fail_total").counter();
        Counter register = registry.find("user_register_total").counter();
        assertThat(success).isNotNull();
        assertThat(fail).isNotNull();
        assertThat(register).isNotNull();
        assertThat(success.count()).isEqualTo(0.0);

        metrics.onLoginSuccess();
        metrics.onLoginSuccess();
        metrics.onLoginFail();
        metrics.onRegister();

        assertThat(success.count()).isEqualTo(2.0);
        assertThat(fail.count()).isEqualTo(1.0);
        assertThat(register.count()).isEqualTo(1.0);
    }
}

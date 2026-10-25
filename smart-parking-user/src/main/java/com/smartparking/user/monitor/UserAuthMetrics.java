package com.smartparking.user.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 用户模块认证埋点。
 *
 * <p>暴露指标：</p>
 * <ul>
 *   <li>{@code user_login_success_total}：登录成功累计次数</li>
 *   <li>{@code user_login_fail_total}：登录失败累计次数（验证码错误/密码错误/禁用等，含全部 RuntimeException 分支）</li>
 *   <li>{@code user_register_total}：注册成功累计次数</li>
 * </ul>
 *
 * <p>登录失败率可用 PromQL {@code rate(user_login_fail_total[5m]) / rate(user_login_success_total[5m])} 观测，
 * 突增通常意味着撞库或验证码服务异常。</p>
 */
@Component
public class UserAuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFail;
    private final Counter register;

    public UserAuthMetrics(MeterRegistry registry) {
        this.loginSuccess = Counter.builder("user_login_success_total")
                .description("登录成功累计次数")
                .register(registry);
        this.loginFail = Counter.builder("user_login_fail_total")
                .description("登录失败累计次数")
                .register(registry);
        this.register = Counter.builder("user_register_total")
                .description("注册成功累计次数")
                .register(registry);
    }

    public void onLoginSuccess() {
        loginSuccess.increment();
    }

    public void onLoginFail() {
        loginFail.increment();
    }

    public void onRegister() {
        register.increment();
    }
}

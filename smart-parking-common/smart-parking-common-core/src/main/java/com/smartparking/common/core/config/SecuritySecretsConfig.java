package com.smartparking.common.core.config;

import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.common.core.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.util.StringUtils;

/**
 * 安全密钥装配：把 JWT 签名密钥、AES 数据密钥从「硬编码常量」收口到配置/环境变量。
 *
 * <p>报告 P1-1/P1-4：原先密钥写死在 {@code JwtUtils}、{@code AuthGlobalFilter}、{@code AesUtils} 三处，
 * 且 JWT 与 AES 用的都是同一份仓库可见常量 —— 任何拿到源码的人都能自签 ADMIN token（全站认证失效），
 * 或直接解密库里的手机号/车牌。
 *
 * <p>取值优先级：环境变量 {@code SMART_PARKING_JWT_SECRET} / {@code SMART_PARKING_AES_KEY}
 * → 配置项 {@code smart-parking.jwt-secret} / {@code smart-parking.aes-key} → 内置开发默认值。
 *
 * <p>生产必须显式配置并把 {@code smart-parking.strict-secrets} 置为 true：
 * 此时若仍走默认值，服务直接启动失败，避免"忘记改密钥就上线"。
 *
 * <p>覆盖三类凭据：JWT 签名密钥、AES 数据密钥、服务间内部令牌。
 * 支付回调密钥与模拟支付开关是 payment 专属（其他服务不配置这两个键），
 * 由 {@code PaymentStrictModeGuard} 单独校验，不在本类展开。
 */
@AutoConfiguration
public class SecuritySecretsConfig {

    private static final Logger log = LoggerFactory.getLogger(SecuritySecretsConfig.class);

    @Value("${SMART_PARKING_JWT_SECRET:${smart-parking.jwt-secret:}}")
    private String jwtSecret;

    @Value("${SMART_PARKING_AES_KEY:${smart-parking.aes-key:}}")
    private String aesKey;

    /**
     * 服务间内部令牌。占位符表达式必须与 {@code InternalApiGuardConfig} 完全一致，
     * 否则两处会读到不同的值：守门器用 A、调用方带 B，表现为内部接口恒 403。
     */
    @Value("${SP_INTERNAL_TOKEN:${smart-parking.internal-token:" + Constants.DEFAULT_INTERNAL_TOKEN + "}}")
    private String internalToken;

    /** 生产开关：true 时禁止使用内置默认密钥启动 */
    @Value("${smart-parking.strict-secrets:false}")
    private boolean strictSecrets;

    @PostConstruct
    public void apply() {
        if (StringUtils.hasText(jwtSecret)) {
            JwtUtils.setSecret(jwtSecret);
            log.info("JWT 签名密钥已从配置加载（长度 {}）", jwtSecret.length());
        } else if (strictSecrets) {
            throw new IllegalStateException(
                    "未配置 smart-parking.jwt-secret / SMART_PARKING_JWT_SECRET，生产环境（strict-secrets=true）拒绝启动");
        } else {
            log.warn("未配置 JWT 密钥，正在使用内置开发默认值 —— 仅限本地演示，生产必须通过环境变量注入");
        }

        if (StringUtils.hasText(aesKey)) {
            AesUtils.setKey(aesKey);
            log.info("AES 数据密钥已从配置加载（长度 {}）", aesKey.length());
        } else if (strictSecrets) {
            throw new IllegalStateException(
                    "未配置 smart-parking.aes-key / SMART_PARKING_AES_KEY，生产环境（strict-secrets=true）拒绝启动");
        } else {
            log.warn("未配置 AES 密钥，正在使用内置开发默认值 —— 仅限本地演示，生产必须通过环境变量注入");
        }

        // 内部令牌：默认值等于公开常量，任何拿到源码的人都能直连 8081-8090 调 /internal/** 伪造支付与抬杆
        if (!StringUtils.hasText(internalToken)) {
            if (strictSecrets) {
                throw new IllegalStateException(
                        "未配置内部接口令牌（smart-parking.internal-token / SP_INTERNAL_TOKEN），生产环境（strict-secrets=true）拒绝启动");
            }
            log.warn("未配置内部接口令牌，正在使用内置开发默认值 —— 生产必须通过 SP_INTERNAL_TOKEN 注入");
        } else if (Constants.DEFAULT_INTERNAL_TOKEN.equals(internalToken)) {
            if (strictSecrets) {
                throw new IllegalStateException(
                        "内部接口令牌仍为内置默认值，生产环境（strict-secrets=true）拒绝启动；请通过 SP_INTERNAL_TOKEN 注入随机串");
            }
            log.warn("内部接口令牌等于内置默认值 —— 仅限本地演示，生产必须替换");
        } else {
            log.info("内部接口令牌已从配置加载（自定义值）");
        }
    }
}

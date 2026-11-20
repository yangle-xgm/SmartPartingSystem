package com.smartparking.payment.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分润比例配置化后的取值与拆分规则。
 *
 * <p>比例一旦算错，业主分成金额就会错，且是「已经打给业主的钱」，事后追溯很难。
 * 所以两个方向都要锁住：非法配置必须回退而不是静默取用；拆分后的两笔之和必须精确等于原额。
 */
class PaymentShareRatioTest {

    @Test
    @DisplayName("未配置时用默认 0.7，与历史数据口径一致")
    void nullFallsBackToDefault() {
        assertThat(PaymentRecordServiceImpl.effectiveOwnerShareRatio(null))
                .isEqualByComparingTo("0.7");
    }

    @Test
    @DisplayName("非法配置（≤0、≥1、误写成百分数 70）一律回退 0.7，不得直接采用")
    void illegalRatioFallsBack() {
        for (String illegal : new String[]{"0", "-0.1", "1", "1.5", "70"}) {
            assertThat(PaymentRecordServiceImpl.effectiveOwnerShareRatio(new BigDecimal(illegal)))
                    .as("配置 %s 应回退默认值", illegal)
                    .isEqualByComparingTo(PaymentRecordServiceImpl.DEFAULT_OWNER_SHARE_RATIO);
        }
    }

    @Test
    @DisplayName("合法区间 (0,1) 的配置原样生效")
    void legalRatioIsHonored() {
        for (String legal : new String[]{"0.6", "0.7", "0.85"}) {
            assertThat(PaymentRecordServiceImpl.effectiveOwnerShareRatio(new BigDecimal(legal)))
                    .as("配置 %s 应原样生效", legal)
                    .isEqualByComparingTo(legal);
        }
    }

    @Test
    @DisplayName("拆分：业主 7 成、物业 3 成")
    void splitByRatio() {
        BigDecimal[] split = PaymentRecordServiceImpl.splitShareAmount(new BigDecimal("15.00"), new BigDecimal("0.7"));
        assertThat(split[0]).isEqualByComparingTo("10.50");
        assertThat(split[1]).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("拆分：业主金额四舍五入后，物业取差额，两者之和必须精确等于原额（不丢分）")
    void splitNeverLosesCent() {
        BigDecimal[] split = PaymentRecordServiceImpl.splitShareAmount(new BigDecimal("33.33"), new BigDecimal("0.7"));
        assertThat(split[0]).isEqualByComparingTo("23.33");
        assertThat(split[1]).isEqualByComparingTo("10.00");
        assertThat(split[0].add(split[1])).isEqualByComparingTo("33.33");
    }
}

package com.smartparking.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * 网关 Sentinel 限流规则（按计划书 3.2 节分级 QPS）
 * - 登录/注册 10 QPS（防爆破）
 * - 车位查询 50 QPS（高频读）
 * - 支付回调 100 QPS
 * - 其他业务 30 QPS
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void initRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(rule("smart-parking-user", 10));      // 登录注册
        rules.add(rule("smart-parking-parking", 50));  // 车位查询
        rules.add(rule("smart-parking-order", 30));
        rules.add(rule("smart-parking-payment", 100));  // 支付回调
        rules.add(rule("smart-parking-vehicle", 30));
        rules.add(rule("smart-parking-device", 30));
        rules.add(rule("smart-parking-notify", 30));
        rules.add(rule("smart-parking-stats", 30));
        GatewayRuleManager.loadRules(rules);
    }

    private GatewayFlowRule rule(String routeId, int qps) {
        GatewayFlowRule r = new GatewayFlowRule(routeId);
        r.setCount(qps);
        r.setIntervalSec(1);
        r.setGrade(RuleConstant.FLOW_GRADE_QPS);
        r.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return r;
    }
}

package com.smartparking.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.order.po.BillingRule;

/**
 * 计费规则服务接口
 */
public interface BillingRuleService extends IService<BillingRule> {

    /**
     * 获取适用的计费规则
     */
    BillingRule getApplicableRule(Integer vehicleType, String plateColor);
}

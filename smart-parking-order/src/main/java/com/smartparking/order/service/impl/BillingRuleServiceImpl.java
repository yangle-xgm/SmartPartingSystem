package com.smartparking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.order.mapper.BillingRuleMapper;
import com.smartparking.order.po.BillingRule;
import com.smartparking.order.service.BillingRuleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 计费规则服务实现
 */
@Service
public class BillingRuleServiceImpl extends ServiceImpl<BillingRuleMapper, BillingRule> implements BillingRuleService {

    @Override
    public BillingRule getApplicableRule(Integer vehicleType, String plateColor) {
        // 优先匹配精确规则（车辆类型+车牌颜色）
        if (StringUtils.hasText(plateColor)) {
            BillingRule rule = this.getOne(new LambdaQueryWrapper<BillingRule>()
                    .eq(BillingRule::getStatus, 1)
                    .eq(BillingRule::getVehicleType, vehicleType)
                    .eq(BillingRule::getPlateColor, plateColor)
                    .last("LIMIT 1"));
            if (rule != null) {
                return rule;
            }
        }

        // 其次匹配仅车辆类型的规则
        BillingRule rule = this.getOne(new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getStatus, 1)
                .eq(BillingRule::getVehicleType, vehicleType)
                .isNull(BillingRule::getPlateColor)
                .last("LIMIT 1"));
        if (rule != null) {
            return rule;
        }

        // 最后匹配全部类型的默认规则
        return this.getOne(new LambdaQueryWrapper<BillingRule>()
                .eq(BillingRule::getStatus, 1)
                .eq(BillingRule::getVehicleType, 0)
                .last("LIMIT 1"));
    }
}

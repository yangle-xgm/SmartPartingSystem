package com.smartparking.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.order.po.BillingRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计费规则Mapper
 */
@Mapper
public interface BillingRuleMapper extends BaseMapper<BillingRule> {
}

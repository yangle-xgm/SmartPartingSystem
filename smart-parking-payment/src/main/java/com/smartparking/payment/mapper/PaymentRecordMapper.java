package com.smartparking.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.payment.po.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录Mapper
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}

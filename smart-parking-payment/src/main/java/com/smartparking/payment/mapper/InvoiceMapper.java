package com.smartparking.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.payment.po.Invoice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 电子发票 Mapper
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {
}

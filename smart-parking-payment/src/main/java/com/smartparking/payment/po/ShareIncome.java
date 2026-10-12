package com.smartparking.payment.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车位共享分润记录
 */
@Data
@TableName("share_income")
public class ShareIncome {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车位ID */
    private Long spaceId;

    /** 车位业主ID（分润对象） */
    private Long ownerId;

    /** 支付记录ID */
    private Long paymentId;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 业主分润比例（如0.7=70%） */
    private BigDecimal ownerRatio;

    /** 业主应得分润 */
    private BigDecimal ownerAmount;

    /** 物业所得 */
    private BigDecimal propertyAmount;

    private LocalDateTime createTime;
}

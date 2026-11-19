package com.smartparking.order.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 停车订单查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "停车订单查询条件")
public class ParkingOrderQuery extends PageQuery {

    @Schema(description = "订单状态（0进行中 1已完成 2已取消）")
    private Integer status;

    @Schema(description = "支付状态（0未支付 1已支付 2已退款）")
    private Integer payStatus;

    @Schema(description = "车辆ID列表（逗号分隔，业主查自己的订单）")
    private String vehicleIds;

    /** 关键字：订单号 / 车位号模糊匹配；传完整车牌时按密文等值匹配（报告 P2-12：原先只过滤当前页，跨页搜不到） */
    @Schema(description = "关键字（订单号/车位号/完整车牌）")
    private String keyword;
}

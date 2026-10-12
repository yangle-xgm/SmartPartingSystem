package com.smartparking.parking.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车位查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "车位查询条件")
public class ParkingSpaceQuery extends PageQuery {

    @Schema(description = "所属区域")
    private String area;

    @Schema(description = "类型（0固定车位 1临时车位 2访客车位）")
    private Integer type;

    @Schema(description = "状态（0空闲 1已占用 2预约中 3维护中）")
    private Integer status;
}

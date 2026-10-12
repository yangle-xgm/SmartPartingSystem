package com.smartparking.parking.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 车位实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_space")
@Schema(description = "车位")
public class ParkingSpace extends BaseEntity {

    @Schema(description = "车位编号")
    private String spaceNo;

    @Schema(description = "所属区域")
    private String area;

    @Schema(description = "楼层")
    private String floor;

    @Schema(description = "类型（0固定车位 1临时车位 2访客车位）")
    private Integer type;

    @Schema(description = "状态（0空闲 1已占用 2预约中 3维护中）")
    private Integer status;

    @Schema(description = "业主ID")
    private Long ownerId;

    @Schema(description = "月租价格")
    private BigDecimal monthPrice;

    @Schema(description = "是否共享（0不共享 1共享，业主闲置时开放给临停）")
    private Integer isShared;

    @Schema(description = "所属小区ID（默认1）")
    private Long communityId;
}

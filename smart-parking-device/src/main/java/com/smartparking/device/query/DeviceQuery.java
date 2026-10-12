package com.smartparking.device.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备查询条件")
public class DeviceQuery extends PageQuery {

    @Schema(description = "设备类型（1道闸 2摄像头 3地磁 4显示屏）")
    private Integer deviceType;

    @Schema(description = "状态（0离线 1在线 2故障）")
    private Integer status;
}

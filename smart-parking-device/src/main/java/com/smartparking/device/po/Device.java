package com.smartparking.device.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device")
@Schema(description = "设备")
public class Device extends BaseEntity {

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型（1道闸 2摄像头 3地磁 4显示屏）")
    private Integer deviceType;

    @Schema(description = "安装位置")
    private String location;

    @Schema(description = "MQTT通信主题")
    private String mqttTopic;

    @Schema(description = "状态（0离线 1在线 2故障）")
    private Integer status;

    @Schema(description = "最后心跳时间")
    private LocalDateTime lastHeartbeat;
}

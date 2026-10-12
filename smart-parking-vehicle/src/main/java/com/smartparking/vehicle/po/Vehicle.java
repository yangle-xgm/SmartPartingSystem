package com.smartparking.vehicle.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车辆实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle")
@Schema(description = "车辆")
public class Vehicle extends BaseEntity {

    @Schema(description = "车牌号（AES加密存储）")
    private String plateNo;

    @Schema(description = "车牌颜色（蓝/绿/黄）")
    private String plateColor;

    @Schema(description = "车主ID")
    private Long ownerId;

    @Schema(description = "车辆品牌")
    private String brand;

    @Schema(description = "车身颜色")
    private String color;

    @Schema(description = "类型（0私家车 1访客车辆）")
    private Integer type;

    @Schema(description = "状态（0正常 1黑名单）")
    private Integer status;

    @Schema(description = "月卡到期时间（NULL表示非月卡车）")
    private java.time.LocalDateTime monthlyExpire;

    /** 车牌号脱敏显示（非数据库字段） */
    @TableField(exist = false)
    private String plateNoMasked;
}

package com.smartparking.stats.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统计数据实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_stats")
@Schema(description = "统计数据")
public class ParkingStats extends BaseEntity {

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "区域")
    private String area;

    @Schema(description = "总车位数")
    private Integer totalSpaces;

    @Schema(description = "已占用车位数")
    private Integer occupiedSpaces;

    @Schema(description = "入场车辆数")
    private Integer entryCount;

    @Schema(description = "出场车辆数")
    private Integer exitCount;

    @Schema(description = "总收入")
    private BigDecimal totalIncome;

    @Schema(description = "平均停车时长（分钟）")
    private Integer avgDuration;
}

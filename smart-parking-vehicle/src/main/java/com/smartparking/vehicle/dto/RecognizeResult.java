package com.smartparking.vehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 车牌识别结果
 */
@Data
@Schema(description = "车牌识别结果")
public class RecognizeResult {

    @Schema(description = "是否识别成功（高置信度且匹配到注册车辆）")
    private boolean recognized;

    @Schema(description = "识别到的车牌号")
    private String plateNo;

    @Schema(description = "脱敏车牌号")
    private String plateNoMasked;

    @Schema(description = "匹配车辆ID（未匹配时为null）")
    private Long vehicleId;

    @Schema(description = "车主ID（未匹配时为null）")
    private Long ownerId;

    @Schema(description = "车辆类型（0私家车 1访客车辆）")
    private Integer vehicleType;

    @Schema(description = "车牌颜色")
    private String plateColor;

    @Schema(description = "识别置信度（0-100）")
    private Integer confidence;

    @Schema(description = "是否模糊识别（置信度低于80%）")
    private boolean fuzzy;

    @Schema(description = "模糊识别建议车牌列表（脱敏）")
    private List<String> suggestList;

    private String message;
}

package com.smartparking.ai.controller;

import com.smartparking.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AI车牌识别：上传图片，返回识别到的车牌号
 */
@Tag(name = "AI车牌识别", description = "上传图片识别车牌号")
@RestController
@RequestMapping("/api/ai/plate")
@RequiredArgsConstructor
public class PlateController {

    @Value("${ai.plate.provider:mock}")
    private String provider;

    @Operation(summary = "上传图片识别车牌")
    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognize(@RequestParam("file") MultipartFile file) {
        String plateNo;
        double confidence;
        if ("baidu".equals(provider)) {
            var result = callBaiduApi(file);
            plateNo = (String) result.get("plateNo");
            confidence = ((Number) result.get("confidence")).doubleValue();
        } else {
            // 模拟：根据文件名返回测试车牌
            plateNo = "京A" + (int) (Math.random() * 90000 + 10000);
            confidence = 95.0;
        }
        return Result.success(Map.of(
                "plateNo", plateNo,
                "confidence", confidence,
                "provider", provider
        ));
    }

    private Map<String, Object> callBaiduApi(MultipartFile file) {
        // 真实实现：获取token -> POST 百度车牌识别接口
        return Map.of("plateNo", "京A12345", "confidence", 98.5);
    }
}

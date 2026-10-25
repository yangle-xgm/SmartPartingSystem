package com.smartparking.ai.controller;

import com.smartparking.ai.service.BaiduPlateService;
import com.smartparking.ai.service.PlateRecognition;
import com.smartparking.ai.monitor.AiCallMetrics;
import com.smartparking.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AI车牌识别：上传图片，返回识别到的车牌号。
 *
 * <p>provider=baidu 且已配置 AK/SK 时调用百度车牌识别真实接口；
 * 未配置、或调用异常时自动降级到本地 mock（随机车牌），保证服务可用。
 */
@Slf4j
@Tag(name = "AI车牌识别", description = "上传图片识别车牌号")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/plate")
public class PlateController {

    private final AiCallMetrics aiCallMetrics;

    @Value("${ai.plate.provider:mock}")
    private String provider;

    @Value("${ai.plate.baidu-api-key:}")
    private String apiKey;

    @Value("${ai.plate.baidu-secret-key:}")
    private String secretKey;

    @Value("${ai.plate.baidu-token-url:https://aip.baidubce.com/oauth/2.0/token}")
    private String tokenUrl;

    @Value("${ai.plate.baidu-recognize-url:https://aip.baidubce.com/rest/2.0/ocr/v1/license_plate}")
    private String recognizeUrl;

    private BaiduPlateService baiduPlateService;

    @PostConstruct
    public void init() {
        this.baiduPlateService = new BaiduPlateService(
                apiKey, secretKey, tokenUrl, recognizeUrl, new RestTemplate());
    }

    @Operation(summary = "上传图片识别车牌")
    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognize(@RequestParam("file") MultipartFile file) {
        String plateNo;
        double confidence;
        String plateColor = "";

        if ("baidu".equals(provider) && baiduPlateService.isConfigured()) {
            try {
                PlateRecognition r = baiduPlateService.recognize(file.getBytes());
                aiCallMetrics.onRealCall("plate");
                plateNo = r.number();
                confidence = r.confidence();
                plateColor = r.color();
            } catch (java.io.IOException e) {
                log.warn("读取上传文件字节失败，降级到模拟模式: {}", e.getMessage());
                aiCallMetrics.onMockFallback("plate");
                var mock = mockResult();
                plateNo = mock.plateNo;
                confidence = mock.confidence;
            } catch (Exception e) {
                log.warn("百度车牌识别调用失败，降级到模拟模式: {}", e.getMessage());
                aiCallMetrics.onMockFallback("plate");
                var mock = mockResult();
                plateNo = mock.plateNo;
                confidence = mock.confidence;
            }
        } else {
            aiCallMetrics.onMockFallback("plate");
            var mock = mockResult();
            plateNo = mock.plateNo;
            confidence = mock.confidence;
        }

        return Result.success(Map.of(
                "plateNo", plateNo,
                "confidence", confidence,
                "provider", provider,
                "plateColor", plateColor
        ));
    }

    /** 本地模拟：用于未配置百度密钥或百度不可达时的兜底 */
    private MockResult mockResult() {
        return new MockResult("京A" + (int) (Math.random() * 90000 + 10000), 95.0);
    }

    private record MockResult(String plateNo, double confidence) {
    }
}

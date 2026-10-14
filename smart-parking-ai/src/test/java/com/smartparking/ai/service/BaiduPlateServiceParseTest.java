package com.smartparking.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 解析逻辑纯测试：不依赖网络，验证对百度两种返回形态与异常形态的解析。
 */
public class BaiduPlateServiceParseTest {

    @Test
    void parseArrayShape() {
        String json = "{\"words_result\":[{\"number\":\"京A12345\",\"color\":\"blue\"," +
                "\"probability\":[0.999,0.998,0.999,0.997,0.999,0.998,0.999]}],\"log_id\":1}";
        PlateRecognition r = BaiduPlateService.parseResponse(json);
        assertEquals("京A12345", r.number());
        assertEquals("blue", r.color());
        // 取 probability 最小值换算，0.997*100 = 99.7
        assertEquals(99.7, r.confidence(), 0.001);
    }

    @Test
    void parseObjectShape() {
        String json = "{\"words_result\":{\"number\":\"沪B88888\",\"color\":\"yellow\"," +
                "\"probability\":[0.95,0.94,0.96,0.93,0.95,0.97,0.96]},\"words_result_num\":1}";
        PlateRecognition r = BaiduPlateService.parseResponse(json);
        assertEquals("沪B88888", r.number());
        assertEquals("yellow", r.color());
        // min=0.93 -> 93.0
        assertEquals(93.0, r.confidence(), 0.001);
    }

    @Test
    void parseErrorResponseThrows() {
        String json = "{\"error_code\":110,\"error_msg\":\"Access token invalid or no longer valid\"}";
        assertThrows(IllegalArgumentException.class, () -> BaiduPlateService.parseResponse(json));
    }

    @Test
    void parseEmptyResultThrows() {
        String json = "{\"words_result\":[]}";
        assertThrows(IllegalArgumentException.class, () -> BaiduPlateService.parseResponse(json));
    }

    @Test
    void parseMissingWordsResultThrows() {
        String json = "{\"log_id\":123}";
        assertThrows(IllegalArgumentException.class, () -> BaiduPlateService.parseResponse(json));
    }
}

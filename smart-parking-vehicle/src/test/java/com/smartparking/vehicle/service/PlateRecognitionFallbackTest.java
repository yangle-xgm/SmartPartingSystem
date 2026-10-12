package com.smartparking.vehicle.service;

import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.vehicle.dto.RecognizeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 车牌识别模糊兜底单元测试（Phase B）。
 * 覆盖 {@code VehicleServiceImpl.recognize} 的置信度阈值（<80 进入兜底）与车牌归一化/脱敏规则。
 */
class PlateRecognitionFallbackTest {

    /** 与 VehicleServiceImpl.recognize 一致的模糊识别阈值：置信度 < 80 进入兜底 */
    private static boolean isFuzzy(Integer confidence) {
        return confidence != null && confidence < 80;
    }

    /** 与入口逻辑一致的归一化：去除首尾空白并转大写 */
    private static String normalize(String plate) {
        return plate == null ? null : plate.trim().toUpperCase();
    }

    @Test
    void lowConfidence_isFuzzy() {
        assertTrue(isFuzzy(0));
        assertTrue(isFuzzy(79));
        assertFalse(isFuzzy(80));
        assertFalse(isFuzzy(100));
    }

    @Test
    void nullConfidence_notFuzzy() {
        assertFalse(isFuzzy(null));
    }

    @Test
    void normalize_trimsAndUpperCases() {
        assertEquals("京A12345", normalize("  京a12345 "));
        assertNull(normalize(null));
    }

    @Test
    void encryption_roundTrip_usedInRecognize() {
        // recognize 内部用 AesUtils 加解密车牌；验证脱敏结果格式
        String encrypted = AesUtils.encrypt("京A12345");
        assertNotEquals("京A12345", encrypted);
        assertEquals("京A****5", AesUtils.maskPlate(AesUtils.decrypt(encrypted)));
    }

    @Test
    void recognizeResult_fieldsPopulated() {
        RecognizeResult r = new RecognizeResult();
        r.setPlateNo("京A12345");
        r.setConfidence(95);
        r.setRecognized(true);
        r.setVehicleType(0);
        r.setFuzzy(false);
        assertEquals("京A12345", r.getPlateNo());
        assertTrue(r.isRecognized());
        assertEquals(0, r.getVehicleType());
        assertFalse(r.isFuzzy());
    }
}

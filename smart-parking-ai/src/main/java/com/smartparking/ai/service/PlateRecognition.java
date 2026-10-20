package com.smartparking.ai.service;

/**
 * 一次车牌识别的结果。
 *
 * @param number     车牌号，如 "京A12345"
 * @param color     车牌颜色（百度枚举：blue/green/yellow/black/white 等）
 * @param confidence 综合置信度（0-100，由百度 probability 数组最小值换算，越严谨）
 */
public record PlateRecognition(String number, String color, double confidence) {
}

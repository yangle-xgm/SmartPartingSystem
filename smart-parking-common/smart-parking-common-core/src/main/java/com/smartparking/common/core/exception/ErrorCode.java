package com.smartparking.common.core.exception;

import lombok.Getter;

/**
 * 统一错误码
 * 规则：
 *  4xx 客户端错误（认证/权限/参数）
 *  5xxx 业务错误（按模块分段）
 *  51xx 用户/认证
 *  52xx 车位
 *  53xx 订单
 *  54xx 支付
 *  55xx 车辆
 *  56xx 设备
 *  57xx 通知
 */
@Getter
public enum ErrorCode {

    // 通用
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    // 用户/认证 51xx
    LOGIN_FAILED(5101, "用户名或密码错误"),
    USER_DISABLED(5102, "账号已被禁用"),
    USER_NOT_FOUND(5103, "用户不存在"),
    USERNAME_EXISTS(5104, "用户名已存在"),

    // 车位 52xx
    SPACE_NOT_FOUND(5201, "车位不存在"),
    SPACE_UNAVAILABLE(5202, "车位当前不可预约或已占用"),
    SPACE_FULL(5203, "暂无空闲车位"),
    RESERVATION_NOT_FOUND(5204, "预约记录不存在"),
    RESERVATION_ALREADY_FINISHED(5205, "预约已结束或已取消"),

    // 订单 53xx
    ORDER_NOT_FOUND(5301, "订单不存在"),
    ORDER_STATUS_ERROR(5302, "订单状态异常"),
    ORDER_ONGOING_EXISTS(5303, "该车辆已有进行中的订单"),
    ORDER_BILLING_RULE_MISSING(5304, "未找到适用的计费规则"),

    // 支付 54xx
    PAY_NOT_FOUND(5401, "支付记录不存在"),
    PAY_STATUS_ERROR(5402, "支付状态异常"),
    PAY_AMOUNT_INVALID(5403, "支付金额非法"),

    // 车辆 55xx
    VEHICLE_NOT_FOUND(5501, "车辆不存在"),
    PLATE_NOT_RECOGNIZED(5502, "未识别到注册车辆"),
    VEHICLE_ALREADY_IN(5503, "车辆已在场内"),

    // 设备 56xx
    DEVICE_NOT_FOUND(5601, "设备不存在"),
    GATE_ERROR(5602, "道闸操作失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

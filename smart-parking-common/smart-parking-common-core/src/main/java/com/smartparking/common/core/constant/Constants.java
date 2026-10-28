package com.smartparking.common.core.constant;

/**
 * 系统常量
 */
public class Constants {

    /** UTF-8 */
    public static final String UTF8 = "UTF-8";

    /** Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Token请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 用户ID在Token中的Key */
    public static final String CLAIM_USER_ID = "userId";

    /** 用户名在Token中的Key */
    public static final String CLAIM_USERNAME = "username";

    /** 角色在Token中的Key */
    public static final String CLAIM_ROLE = "role";

    /** 小区ID在Token中的Key */
    public static final String CLAIM_COMMUNITY_ID = "communityId";

    /** 小区ID请求头（网关透传） */
    public static final String HEADER_COMMUNITY_ID = "X-Community-Id";

    /** Redis Key前缀：用户Token */
    public static final String REDIS_TOKEN_PREFIX = "smart:parking:token:";

    /** Redis Key前缀：车位状态 */
    public static final String REDIS_PARKING_SPACE_STATUS = "smart:parking:space:status:";

    /** Redis Key前缀：车位可用数统计 */
    public static final String REDIS_PARKING_AVAILABLE = "smart:parking:available:count";

    /** 车位状态：空闲 */
    public static final int SPACE_STATUS_FREE = 0;

    /** 车位状态：已占用 */
    public static final int SPACE_STATUS_OCCUPIED = 1;

    /** 车位状态：预约中 */
    public static final int SPACE_STATUS_RESERVED = 2;

    /** 车位状态：维护中 */
    public static final int SPACE_STATUS_MAINTENANCE = 3;

    /** 订单状态：进行中 */
    public static final int ORDER_STATUS_ONGOING = 0;

    /** 订单状态：已完成 */
    public static final int ORDER_STATUS_COMPLETED = 1;

    /** 订单状态：已取消 */
    public static final int ORDER_STATUS_CANCELLED = 2;

    /** 支付状态：未支付 */
    public static final int PAY_STATUS_UNPAID = 0;

    /** 支付状态：已支付 */
    public static final int PAY_STATUS_PAID = 1;

    /** 支付状态：已退款 */
    public static final int PAY_STATUS_REFUNDED = 2;

    /** 支付状态：已关闭（支付超时未付款，与"已退款"严格区分，E2E 报告 P0-6） */
    public static final int PAY_STATUS_CLOSED = 3;

    /** 支付状态：支付失败 */
    public static final int PAY_STATUS_FAILED = 4;

    /** 设备类型：道闸 */
    public static final int DEVICE_TYPE_GATE = 1;

    /** 设备类型：摄像头 */
    public static final int DEVICE_TYPE_CAMERA = 2;

    /** 设备类型：地磁 */
    public static final int DEVICE_TYPE_MAGNETIC = 3;

    /** 设备类型：显示屏 */
    public static final int DEVICE_TYPE_DISPLAY = 4;

    /** MQTT Topic: 摄像头识别 */
    public static final String MQTT_TOPIC_CAMERA = "device/camera/%s/plate";

    /** MQTT Topic: 道闸控制 */
    public static final String MQTT_TOPIC_GATE = "device/gate/%s/control";

    private Constants() {
    }
}

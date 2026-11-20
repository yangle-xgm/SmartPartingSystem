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

    /** 支付业务类型：停车订单（biz_id / order_id 均为 parking_order.id） */
    public static final int BIZ_TYPE_PARKING_ORDER = 1;

    /** 支付业务类型：月卡（biz_id = monthly_card_order.id，order_id 为 NULL） */
    public static final int BIZ_TYPE_MONTHLY_CARD = 2;

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

    /**
     * 服务间内部令牌请求头。
     * 网关对 /api/**&#47;internal/** 强制校验该头，服务间经网关调用内部接口时必须携带，
     * 否则返回 403「内部接口禁止外部访问」。
     */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * 内部接口令牌的内置开发默认值。
     *
     * <p>仅供本地演示；{@code smart-parking.strict-secrets=true} 时该值被禁止使用，
     * 与 {@code InternalApiGuardConfig}、{@code SecuritySecretsConfig} 共用同一份常量，
     * 避免默认值散落多处后改一处漏一处。
     */
    public static final String DEFAULT_INTERNAL_TOKEN = "SmartParking-Internal-2026";

    /**
     * 支付回调签名的内置开发默认值。
     *
     * <p>回调 {@code sign = MD5(payNo + "&" + callbackSecret)}，默认值一旦上线即等于
     * 任何人都能伪造「支付成功」回调。同上，{@code strict-secrets=true} 时禁止使用。
     */
    public static final String DEFAULT_CALLBACK_SECRET = "SmartParkingCallback2026";

    /**
     * 沙箱道闸指令签名的内置开发默认盐（{@code sign = MD5(cmd|deviceNo|seq|salt)}）。
     *
     * <p>它算法公开写在代码里，只用于让沙箱链路能在无硬件时跑通「下发→验签→回执」。
     * 接真实道闸时要么换成厂商算法，要么至少换一个随机盐；{@code strict-secrets=true}
     * 时仍在使用该默认值即拒绝启动，避免带着公开盐上线（等于谁都能伪造开闸指令）。
     */
    public static final String DEFAULT_GATE_SIGN_SALT = "SmartParkingGateSandbox";

    private Constants() {
    }
}

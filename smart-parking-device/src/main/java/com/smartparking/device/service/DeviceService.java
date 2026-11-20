package com.smartparking.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.device.po.Device;
import com.smartparking.device.query.DeviceQuery;

import java.util.Map;

/**
 * 设备服务接口
 */
public interface DeviceService extends IService<Device> {

    /**
     * 分页查询设备列表
     */
    Page<Device> pageList(Page<Device> page, DeviceQuery query);

    /**
     * 注册设备
     */
    Device registerDevice(Device device);

    /**
     * 更新设备心跳
     */
    void updateHeartbeat(String deviceNo);

    /**
     * 更新设备状态
     */
    void updateStatus(String deviceNo, Integer status);

    /**
     * 开启道闸
     */
    void openGate(String deviceNo);

    /**
     * 关闭道闸
     */
    void closeGate(String deviceNo);

    /**
     * 设备状态概览
     */
    Map<String, Object> getStatusOverview();

    /**
     * 模拟摄像头识别（同步联动）：发布 MQTT 消息并等待联动结果
     */
    Map<String, Object> mockCameraRecognize(String deviceNo, String plateNo, Integer confidence);

    /**
     * 模拟出口摄像头识别（联动出场：识别 → 订单服务结算 → 开闸）
     */
    Map<String, Object> mockExitRecognize(String deviceNo, String plateNo);

    /**
     * 门岗手动出场放行（按车牌结算出场并开闸）
     */
    Map<String, Object> manualExit(String deviceNo, String plateNo);

    /**
     * 最近的道闸指令流水（含报文、通道、回执、耗时），新的在前。
     * 演示与排障用：直观看到「指令下发了什么、设备有没有回执」。
     */
    java.util.List<Map<String, Object>> recentGateCommands(int limit);

    /**
     * 支付成功联动开闸（内部接口，由 payment 服务在核销成功后调用）。
     *
     * <p>与 {@link #openGate(String)} 的区别：本方法<b>不抛异常</b>。支付已经成功，
     * 开闸失败不能反过来让入账回滚或让调用方重试；失败原因放在返回值里由调用方记日志。
     *
     * @param deviceNo 指定道闸；为空则自动挑选在线的出口道闸
     */
    Map<String, Object> releaseForPayment(String payNo, String orderId, String reason, String deviceNo);
}

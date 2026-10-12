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
}

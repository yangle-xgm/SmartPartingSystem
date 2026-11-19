package com.smartparking.vehicle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.vehicle.dto.RecognizeResult;
import com.smartparking.vehicle.po.Vehicle;

import java.util.List;

/**
 * 车辆服务接口
 */
public interface VehicleService extends IService<Vehicle> {

    /**
     * 获取用户车辆列表
     */
    List<Vehicle> listByUserId(Long userId);

    /**
     * 绑定车辆
     *
     * @param vehicle       前端提交的车辆信息（type/monthlyExpire 等敏感字段会被服务端覆盖）
     * @param currentUserId 网关注入的登录人ID
     * @param currentRole   网关注入的角色（0 业主 / 1 物业 / 2 超管）
     */
    Vehicle bindVehicle(Vehicle vehicle, String currentUserId, String currentRole);

    /**
     * 解绑车辆：归属一律以网关注入的登录人为准，不再信任请求参数
     */
    void unbindVehicle(Long vehicleId, Long currentUserId);

    /**
     * 根据车牌号查询车辆（内部接口）
     */
    Vehicle getByPlateNo(String plateNo);

    /**
     * 更新车辆状态
     */
    void updateStatus(Long vehicleId, Integer status);

    /**
     * 月卡车续费一个月
     */
    void renewMonthly(Long vehicleId);

    /**
     * 车牌识别（含模糊识别兜底）
     *
     * @param plateNo    识别到的车牌号
     * @param confidence 识别置信度（0-100，低于80进入模糊识别兜底）
     */
    RecognizeResult recognize(String plateNo, Integer confidence);
}

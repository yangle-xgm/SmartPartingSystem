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
     */
    Vehicle bindVehicle(Vehicle vehicle);

    /**
     * 解绑车辆
     */
    void unbindVehicle(Long vehicleId, Long userId);

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

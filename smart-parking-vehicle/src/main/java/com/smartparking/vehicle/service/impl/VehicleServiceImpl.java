package com.smartparking.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.vehicle.client.AuditClient;
import com.smartparking.vehicle.dto.RecognizeResult;
import com.smartparking.vehicle.mapper.VehicleMapper;
import com.smartparking.vehicle.monitor.VehicleStatsMetrics;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 车辆服务实现
 */
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl extends ServiceImpl<VehicleMapper, Vehicle> implements VehicleService {

    private final AuditClient auditClient;
    private final VehicleStatsMetrics vehicleStatsMetrics;

    @Override
    public List<Vehicle> listByUserId(Long userId) {
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<>();
        // userId 为空 → 管理端查看全部车辆；非空 → 业主查看我的车辆
        wrapper.eq(userId != null, Vehicle::getOwnerId, userId)
                .orderByDesc(Vehicle::getCreateTime);
        List<Vehicle> vehicles = this.list(wrapper);

        // 脱敏车牌号
        vehicles.forEach(v -> {
            if (StringUtils.hasText(v.getPlateNo())) {
                v.setPlateNoMasked(AesUtils.maskPlate(AesUtils.decrypt(v.getPlateNo())));
            }
        });

        return vehicles;
    }

    @Override
    public Vehicle bindVehicle(Vehicle vehicle) {
        // 检查车牌是否已绑定
        String encryptedPlate = AesUtils.encrypt(vehicle.getPlateNo());
        Long count = this.count(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, encryptedPlate));
        if (count > 0) {
            throw new RuntimeException("该车牌已绑定");
        }

        // 加密存储车牌号
        vehicle.setPlateNo(encryptedPlate);
        vehicle.setStatus(0); // 默认正常

        // 根据车牌颜色判断类型
        if (!StringUtils.hasText(vehicle.getPlateColor())) {
            vehicle.setPlateColor("蓝");
        }

        this.save(vehicle);
        vehicleStatsMetrics.onBind();

        // 操作审计：绑定车辆
        auditClient.record(vehicle.getOwnerId(), null, "绑定车辆", "POST /api/vehicle/bind",
                "{\"plateNo\":\"" + AesUtils.maskPlate(AesUtils.decrypt(vehicle.getPlateNo()))
                        + "\",\"ownerId\":" + vehicle.getOwnerId() + "}", true);
        return vehicle;
    }

    @Override
    public void unbindVehicle(Long vehicleId, Long userId) {
        Vehicle vehicle = this.getById(vehicleId);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        // 业主只能解绑自己的车；管理员/超管可解绑任意车辆
        boolean isAdmin = isAdminOperator();
        if (!isAdmin && !vehicle.getOwnerId().equals(userId)) {
            throw new RuntimeException("无权操作此车辆");
        }
        this.removeById(vehicleId);

        // 操作审计：解绑车辆
        auditClient.record(userId, null, "解绑车辆", "DELETE /api/vehicle/unbind/{id}",
                "{\"vehicleId\":" + vehicleId + "}", true);
    }

    /**
     * 从网关透传请求头判断是否管理员/超管
     */
    private boolean isAdminOperator() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String role = attrs.getRequest().getHeader("X-User-Role");
                if (StringUtils.hasText(role)) {
                    int r = Integer.parseInt(role);
                    return r == 1 || r == 2;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Vehicle getByPlateNo(String plateNo) {
        String encryptedPlate = AesUtils.encrypt(plateNo);
        Vehicle vehicle = this.getOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, encryptedPlate));

        // 脱敏车牌号
        if (vehicle != null && StringUtils.hasText(vehicle.getPlateNo())) {
            vehicle.setPlateNoMasked(AesUtils.maskPlate(AesUtils.decrypt(vehicle.getPlateNo())));
        }

        return vehicle;
    }

    @Override
    public void updateStatus(Long vehicleId, Integer status) {
        Vehicle vehicle = this.getById(vehicleId);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        vehicle.setStatus(status);
        this.updateById(vehicle);
    }

    @Override
    public void renewMonthly(Long vehicleId) {
        Vehicle vehicle = this.getById(vehicleId);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        java.time.LocalDateTime base = vehicle.getMonthlyExpire() != null
                && vehicle.getMonthlyExpire().isAfter(java.time.LocalDateTime.now())
                ? vehicle.getMonthlyExpire() : java.time.LocalDateTime.now();
        vehicle.setMonthlyExpire(base.plusMonths(1));
        vehicle.setType(2); // 月卡车
        this.updateById(vehicle);
    }

    @Override
    public RecognizeResult recognize(String plateNo, Integer confidence) {
        RecognizeResult result = new RecognizeResult();
        result.setPlateNo(plateNo);
        result.setConfidence(confidence);

        // 置信度低于80% → 模糊识别兜底（计划书5.4异常流程）
        if (confidence != null && confidence < 80) {
            result.setRecognized(false);
            result.setFuzzy(true);
            result.setVehicleType(1);

            List<String> suggestList = new ArrayList<>();
            List<Vehicle> all = this.list();
            for (Vehicle vehicle : all) {
                if (StringUtils.hasText(vehicle.getPlateNo())) {
                    String decrypted = AesUtils.decrypt(vehicle.getPlateNo());
                    if (decrypted != null && decrypted.contains(plateNo)) {
                        suggestList.add(AesUtils.maskPlate(decrypted));
                        if (suggestList.size() >= 5) {
                            break;
                        }
                    }
                }
            }
            result.setSuggestList(suggestList);
            return result;
        }

        // 高置信度 → 精确匹配
        Vehicle vehicle = getByPlateNo(plateNo);
        if (vehicle != null) {
            // 黑名单车辆禁止入场
            if (vehicle.getStatus() != null && vehicle.getStatus() == 1) {
                result.setRecognized(false);
                result.setFuzzy(false);
                result.setVehicleType(1);
                result.setPlateNoMasked(vehicle.getPlateNoMasked());
                result.setMessage("该车辆已被列入黑名单，禁止入场");
                auditClient.record(null, null, "黑名单拦截", "POST /api/vehicle/recognize",
                        java.util.Map.of("plateNo", plateNo, "blocked", true), true);
                return result;
            }
            result.setRecognized(true);
            result.setFuzzy(false);
            result.setVehicleId(vehicle.getId());
            result.setOwnerId(vehicle.getOwnerId());
            result.setVehicleType(vehicle.getType());
            result.setPlateColor(vehicle.getPlateColor());
            result.setPlateNoMasked(vehicle.getPlateNoMasked());
        } else {
            // 未匹配到注册车辆 → 按访客车辆处理
            result.setRecognized(false);
            result.setFuzzy(false);
            result.setVehicleType(1);
            result.setPlateNoMasked(AesUtils.maskPlate(plateNo));
        }

        // 操作审计：车牌识别（含模糊兜底）
        auditClient.record(null, null, "车牌识别", "POST /api/vehicle/recognize",
                java.util.Map.of("plateNo", plateNo, "confidence", confidence,
                        "recognized", result.isRecognized(), "fuzzy", result.isFuzzy()), true);
        return result;
    }
}

package com.smartparking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.order.client.AuditClient;
import com.smartparking.order.mapper.VisitorInviteMapper;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.po.VisitorInvite;
import com.smartparking.order.query.VisitorInviteQuery;
import com.smartparking.order.service.ParkingOrderService;
import com.smartparking.order.service.VisitorInviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 访客邀请码服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorInviteServiceImpl extends ServiceImpl<VisitorInviteMapper, VisitorInvite> implements VisitorInviteService {

    /** 邀请码字符集（排除易混淆字符） */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final ParkingOrderService parkingOrderService;
    private final AuditClient auditClient;

    /** 车辆服务地址 */
    @Value("${smart-parking.vehicle-url:http://localhost:8083}")
    private String vehicleUrl;

    /** 车位服务地址 */
    @Value("${smart-parking.parking-url:http://localhost:8082}")
    private String parkingUrl;

    /** 设备服务地址 */
    @Value("${smart-parking.device-url:http://localhost:8087}")
    private String deviceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitorInvite createInvite(Long userId, String plateNo, Integer expireHours) {
        if (plateNo == null || plateNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写访客车牌号");
        }
        // 邀请数量上限：同一业主未过期、未作废的进行中邀请最多 5 个
        long activeCount = this.count(new LambdaQueryWrapper<VisitorInvite>()
                .eq(VisitorInvite::getUserId, userId)
                .eq(VisitorInvite::getStatus, 0)
                .gt(VisitorInvite::getExpireTime, LocalDateTime.now()));
        if (activeCount >= 5) {
            throw new BizException(ErrorCode.INVITE_LIMIT_EXCEEDED);
        }
        int hours = expireHours == null || expireHours <= 0 ? 24 : expireHours;

        VisitorInvite invite = new VisitorInvite();
        invite.setUserId(userId);
        invite.setPlateNo(AesUtils.encrypt(plateNo.trim()));
        invite.setInviteCode(generateUniqueCode());
        invite.setStatus(0);
        invite.setExpireTime(LocalDateTime.now().plusHours(hours));
        invite.setCreateTime(LocalDateTime.now());

        this.save(invite);
        invite.setPlateNoMasked(AesUtils.maskPlate(plateNo.trim()));

        // 操作审计：生成访客邀请码
        auditClient.record(userId, null, "生成访客邀请码", "POST /api/order/invite/create",
                "{\"plateNo\":\"" + invite.getPlateNoMasked() + "\",\"inviteCode\":\"" + invite.getInviteCode()
                        + "\",\"expireHours\":" + hours + "}", true);
        return invite;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder entryByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入邀请码");
        }
        VisitorInvite invite = this.getOne(new LambdaQueryWrapper<VisitorInvite>()
                .eq(VisitorInvite::getInviteCode, inviteCode.trim().toUpperCase()));
        if (invite == null) {
            throw new BizException(ErrorCode.INVITE_NOT_FOUND);
        }
        checkValid(invite, "邀请码");
        return doVisitorEntry(invite, "凭码入场");
    }

    /**
     * 车牌命中白名单自动入场。
     *
     * <p>E2E 报告 P1-1：传播行为必须是 NOT_SUPPORTED —— 调用方 deviceEntry() 外层是
     * {@code @Transactional}，若此处用默认 REQUIRED 加入外层事务，未注册车牌抛异常后
     * Spring 将外层事务标记 rollback-only，外层 catch 后继续提交触发
     * UnexpectedRollbackException（"Transaction rolled back because it has been marked as rollback-only"）。
     * NOT_SUPPORTED 挂起外层事务独立运行，异常不污染外层，内部 DB 更新各自自动提交。</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ParkingOrder entryByPlateNo(String plateNo) {
        if (plateNo == null || plateNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "车牌号为空");
        }
        String normalized = plateNo.trim().toUpperCase();

        // 查询全部有效白名单（未作废），逐个解密比对车牌（车牌 AES 存储，无法在 SQL 中解密）
        List<VisitorInvite> actives = this.list(new LambdaQueryWrapper<VisitorInvite>()
                .eq(VisitorInvite::getStatus, 0));
        VisitorInvite matched = null;
        for (VisitorInvite invite : actives) {
            if (invite.getExpireTime() != null && invite.getExpireTime().isBefore(LocalDateTime.now())) {
                invite.setStatus(2);
                this.updateById(invite);
                continue;
            }
            String stored = AesUtils.decrypt(invite.getPlateNo());
            if (normalized.equals(stored == null ? null : stored.trim().toUpperCase())) {
                matched = invite;
            }
        }
        if (matched == null) {
            throw new BizException(ErrorCode.INVITE_NOT_WHITELISTED);
        }
        return doVisitorEntry(matched, "车牌识别入场");
    }

    /**
     * 校验邀请记录状态，过期则置为已过期
     */
    private void checkValid(VisitorInvite invite, String label) {
        if (invite.getStatus() == 1) {
            throw new BizException(ErrorCode.INVITE_ALREADY_USED);
        }
        if (invite.getStatus() == 3) {
            throw new BizException(ErrorCode.INVITE_CANCELLED);
        }
        if (invite.getExpireTime() != null && invite.getExpireTime().isBefore(LocalDateTime.now())) {
            invite.setStatus(2);
            this.updateById(invite);
            throw new BizException(ErrorCode.INVITE_EXPIRED);
        }
    }

    /**
     * 访客入场公共流程：绑定访客车辆 → 分配空闲车位 → 创建入场订单 → 更新入场时间 → 自动开闸
     * 白名单有效期内可多次进出，入场不置为已使用，仅刷新最近入场时间
     */
    private ParkingOrder doVisitorEntry(VisitorInvite invite, String channel) {
        String plateNo = AesUtils.decrypt(invite.getPlateNo());
        if (!StringUtils.hasText(plateNo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "访客车牌数据异常");
        }

        // 1. 绑定访客车辆（绑定到邀请业主名下，type=1）
        Long vehicleId = bindVisitorVehicle(invite.getUserId(), plateNo);

        // 2. 分配空闲车位
        Map<String, Object> space = findAvailableSpace();
        if (space == null) {
            throw new BizException(ErrorCode.SPACE_FULL);
        }
        Long spaceId = Long.valueOf(String.valueOf(space.get("id")));
        String spaceNo = String.valueOf(space.get("spaceNo"));

        // 3. 创建入场订单（访客车辆 type=1，匹配访客计费规则）
        ParkingOrder order = parkingOrderService.createEntryOrder(vehicleId, plateNo, spaceId, spaceNo, 1, null);

        // 4. 刷新最近入场时间（白名单有效期内可重复入场，不作废）
        invite.setUsedTime(LocalDateTime.now());
        this.updateById(invite);

        // 5. 自动开启道闸（MQTT联动）
        autoOpenGate();

        // 操作审计：访客入场
        auditClient.record(invite.getUserId(), null, "访客" + channel, "POST /api/order/invite/entry",
                "{\"channel\":\"" + channel + "\",\"plateNo\":\"" + plateNo
                        + "\",\"spaceNo\":\"" + spaceNo + "\"}", true);
        return order;
    }

    @Override
    public void invalidate(Long id, Long userId) {
        VisitorInvite invite = this.getById(id);
        if (invite == null) {
            throw new BizException(ErrorCode.INVITE_NOT_FOUND);
        }
        if (!invite.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作此邀请码");
        }
        invite.setStatus(3);
        this.updateById(invite);
    }

    @Override
    public Page<VisitorInvite> pageList(Page<VisitorInvite> page, VisitorInviteQuery query) {
        LambdaQueryWrapper<VisitorInvite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, VisitorInvite::getUserId, query.getUserId())
                .eq(query.getStatus() != null, VisitorInvite::getStatus, query.getStatus())
                .orderByDesc(VisitorInvite::getCreateTime);
        Page<VisitorInvite> result = this.page(page, wrapper);
        // 车牌脱敏
        result.getRecords().forEach(invite -> {
            if (StringUtils.hasText(invite.getPlateNo())) {
                invite.setPlateNoMasked(AesUtils.maskPlate(AesUtils.decrypt(invite.getPlateNo())));
            }
        });
        return result;
    }

    /**
     * 生成6位唯一邀请码
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARS.charAt(ThreadLocalRandom.current().nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            Long count = this.count(new LambdaQueryWrapper<VisitorInvite>()
                    .eq(VisitorInvite::getInviteCode, code));
            if (count == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "生成邀请码失败，请重试");
    }

    /**
     * 绑定访客车辆（绑定到邀请业主名下，type=1）
     * 若车牌已存在（上次来访过），直接复用已有车辆ID，不重复绑定
     */
    private Long bindVisitorVehicle(Long ownerId, String plateNo) {
        try {
            // 先查车牌是否已绑定
            String queryUrl = vehicleUrl + "/api/vehicle/internal/info/" + java.net.URLEncoder.encode(plateNo, "UTF-8");
            try {
                String existingBody = restTemplate.getForObject(java.net.URI.create(queryUrl), String.class);
                Map<String, Object> existingResult = parseJson(existingBody);
                if (existingResult.get("code") != null && "200".equals(String.valueOf(existingResult.get("code")))) {
                    Object data = existingResult.get("data");
                    if (data != null) {
                        Map<String, Object> vehicleMap = (Map<String, Object>) data;
                        if (vehicleMap.get("id") != null) {
                            log.info("车牌 {} 已存在，复用车辆ID={}", plateNo, vehicleMap.get("id"));
                            return Long.valueOf(String.valueOf(vehicleMap.get("id")));
                        }
                    }
                }
            } catch (Exception ignore) { /* 接口不存在或查询失败，继续走绑定 */ }

            // 未绑定过，创建新车辆
            Map<String, Object> vehicle = new HashMap<>();
            vehicle.put("plateNo", plateNo);
            vehicle.put("plateColor", "蓝");
            vehicle.put("type", 1);
            vehicle.put("ownerId", ownerId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = vehicleUrl + "/api/vehicle/bind";
            String body = restTemplate.postForObject(url, new HttpEntity<>(vehicle, headers), String.class);
            Map<String, Object> result = parseJson(body);
            Object data = result.get("data");
            if (data == null || result.get("code") == null || !"200".equals(String.valueOf(result.get("code")))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "绑定访客车辆失败");
            }
            Map<String, Object> dataMap = (Map<String, Object>) data;
            return Long.valueOf(String.valueOf(dataMap.get("id")));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("绑定访客车辆失败：plateNo={}, err={}", plateNo, e.getMessage());
            throw new BizException(ErrorCode.PARAM_INVALID, "绑定访客车辆失败，请确认车牌未重复绑定");
        }
    }

    /**
     * 自动开启入口道闸（MQTT联动）
     */
    private void autoOpenGate() {
        try {
            // 查询设备服务获取入口道闸（deviceType=1）
            String listUrl = deviceUrl + "/api/device/list?deviceType=1&current=1&size=10";
            String body = restTemplate.getForObject(listUrl, String.class);
            Map<String, Object> result = parseJson(body);
            if (result.get("code") != null && "200".equals(String.valueOf(result.get("code")))) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null && data.get("records") != null) {
                    List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                    if (!records.isEmpty()) {
                        String deviceNo = String.valueOf(records.get(0).get("deviceNo"));
                        String openUrl = deviceUrl + "/api/device/gate/open?deviceNo=" + deviceNo;
                        restTemplate.postForObject(openUrl, null, String.class);
                        log.info("访客入场自动开闸：deviceNo={}", deviceNo);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("自动开闸失败（不影响入场）：{}", e.getMessage());
        }
    }

    /**
     * 调用车位服务获取空闲车位
     */
    private Map<String, Object> findAvailableSpace() {
        try {
            String url = parkingUrl + "/api/parking/available";
            String body = restTemplate.getForObject(url, String.class);
            Map<String, Object> result = parseJson(body);
            if (result.get("code") == null || !"200".equals(String.valueOf(result.get("code")))) {
                return null;
            }
            List<Map<String, Object>> spaces = (List<Map<String, Object>>) result.get("data");
            if (spaces == null || spaces.isEmpty()) {
                return null;
            }
            return spaces.get(0);
        } catch (Exception e) {
            log.error("获取空闲车位失败：{}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJson(String body) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
    }
}

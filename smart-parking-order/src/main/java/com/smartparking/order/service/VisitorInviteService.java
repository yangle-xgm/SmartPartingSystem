package com.smartparking.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.po.VisitorInvite;
import com.smartparking.order.query.VisitorInviteQuery;

/**
 * 访客邀请码服务
 */
public interface VisitorInviteService extends IService<VisitorInvite> {

    /**
     * 业主生成邀请码
     *
     * @param userId      邀请业主ID
     * @param plateNo     访客车牌号
     * @param expireHours 有效期（小时）
     * @return 邀请记录（含邀请码）
     */
    VisitorInvite createInvite(Long userId, String plateNo, Integer expireHours);

    /**
     * 访客凭码入场：校验邀请码 → 绑定访客车辆 → 分配空闲车位 → 创建入场订单
     *
     * @param inviteCode 邀请码
     * @return 入场订单
     */
    ParkingOrder entryByInviteCode(String inviteCode);

    /**
     * 访客车辆自动识别入场（车牌白名单）：车牌命中有效白名单 → 绑定访客车辆 → 分配空闲车位 → 创建入场订单
     * 有效期内可多次进出，入场不使白名单失效
     *
     * @param plateNo 车牌号（明文）
     * @return 入场订单
     */
    ParkingOrder entryByPlateNo(String plateNo);

    /**
     * 作废邀请码（仅邀请人可操作）
     */
    void invalidate(Long id, Long userId);

    /**
     * 分页查询（脱敏车牌）
     */
    Page<VisitorInvite> pageList(Page<VisitorInvite> page, VisitorInviteQuery query);
}

package com.smartparking.device.gate;

import com.smartparking.device.po.Device;

/**
 * 道闸厂商适配器。
 *
 * <p>接入新厂商只需实现本接口并注册为 Spring Bean：
 * {@link #vendor()} 返回厂商码，设备表 device.vendor 填同一个码即可生效，
 * 业务代码（进场自动开闸、出口放行、手动开闸）完全不用改。
 *
 * <p>这就是「设备厂商渠道」的扩展点：协议差异（报文格式、鉴权、ACK 主题）全部收敛在适配器内部。
 */
public interface GateAdapter {

    /** 厂商码，需与 device.vendor 一致 */
    String vendor();

    /** 开闸 */
    GateResult open(Device device, String reason);

    /** 关闸 */
    GateResult close(Device device);
}

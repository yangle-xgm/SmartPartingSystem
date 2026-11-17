package com.smartparking.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.po.VisitorInvite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段序列化契约测试（E2E 报告 P0-4 的防回归闸门）。
 *
 * <p>历史问题：接口直接返回实体时把 AES 密文一并吐出，前端渲染成乱码 Base64。
 * 约定：
 * <ul>
 *   <li>{@link VisitorInvite#getPlateNo()} 仅用于存储/比对，对外 JSON 必须不存在；</li>
 *   <li>{@link ParkingOrder#getPlateNo()} 是业务主字段（列表/详情需展示明文），必须保留。</li>
 * </ul>
 * 若有人误删 {@code @JsonIgnore}，本测试立即失败。
 */
class PlateNoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("访客邀请的密文车牌不得出现在 JSON 中")
    void visitorInvite_hidesCipherPlate() throws Exception {
        VisitorInvite invite = new VisitorInvite();
        invite.setPlateNo("VsuQrPh5TIEmli+L567cHA==");
        invite.setPlateNoMasked("湘B****8");

        String json = mapper.writeValueAsString(invite);

        assertFalse(json.contains("VsuQrPh5TIEmli+L567cHA=="), "密文车牌泄漏到接口响应：" + json);
        assertFalse(json.contains("\"plateNo\""), "VisitorInvite 不应序列化 plateNo 字段：" + json);
        assertTrue(json.contains("湘B****8"), "脱敏车牌应正常输出：" + json);
    }

    @Test
    @DisplayName("订单车牌仍需序列化（前端展示解密后的明文）")
    void parkingOrder_keepsPlateNo() throws Exception {
        ParkingOrder order = new ParkingOrder();
        order.setOrderNo("ORD202610281924191211");
        order.setPlateNo("湘A2T817");

        String json = mapper.writeValueAsString(order);

        assertTrue(json.contains("湘A2T817"), "订单车牌不应被隐藏：" + json);
    }
}

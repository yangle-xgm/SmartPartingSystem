package com.smartparking.vehicle.service;

import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.vehicle.client.AuditClient;
import com.smartparking.vehicle.mapper.VehicleMapper;
import com.smartparking.vehicle.monitor.VehicleStatsMetrics;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.impl.VehicleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 车辆服务单元测试（E2E 全链路验证 2026-11-17 两处缺陷的防回归闸门）。
 *
 * <p>历史问题：
 * <ul>
 *   <li>{@code bindVehicle} 先把车牌加密替换成密文再 {@code return} 同一个对象，导致绑定接口
 *       把 AES 密文吐给前端，且 {@code plateNoMasked} 为 null；</li>
 *   <li>重复车牌/车辆不存在仍抛裸 {@link RuntimeException}，网关统一异常处理拿不到错误码。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    private static final String PLAIN_PLATE = "湘A2T817";
    private static final Long OWNER_ID = 1001L;

    @Mock
    private VehicleMapper vehicleMapper;

    @Mock
    private AuditClient auditClient;

    @Mock
    private VehicleStatsMetrics vehicleStatsMetrics;

    private VehicleServiceImpl vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleServiceImpl(auditClient, vehicleStatsMetrics);
        // ServiceImpl 的 baseMapper 由 MyBatis-Plus 注入，单测里手工塞入
        ReflectionTestUtils.setField(vehicleService, "baseMapper", vehicleMapper);
    }

    @Test
    @DisplayName("绑定车辆：入库为 AES 密文，接口返回明文并补脱敏值")
    void bindVehicle_storesCipher_butReturnsPlainText() {
        Vehicle input = new Vehicle();
        input.setPlateNo(PLAIN_PLATE);
        input.setOwnerId(OWNER_ID);

        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        AtomicReference<String> persisted = new AtomicReference<>();
        when(vehicleMapper.insert(any(Vehicle.class))).thenAnswer(invocation -> {
            // 必须在 insert 调用当刻取值：对象随后会被还原成明文
            Vehicle entity = invocation.getArgument(0);
            persisted.set(entity.getPlateNo());
            return 1;
        });

        Vehicle result = vehicleService.bindVehicle(input, String.valueOf(OWNER_ID), "0");

        String cipher = AesUtils.encrypt(PLAIN_PLATE);
        assertEquals(cipher, persisted.get(), "入库必须是 AES 密文，禁止明文落库");
        assertEquals(PLAIN_PLATE, result.getPlateNo(), "绑定接口必须返回明文，不能吐密文");
        assertEquals(AesUtils.maskPlate(PLAIN_PLATE), result.getPlateNoMasked(), "必须同时给出脱敏值");
        assertNotEquals(cipher, result.getPlateNo(), "响应里不得出现 AES 密文");
        assertEquals(0, result.getStatus(), "默认状态应为正常");
        assertEquals("蓝", result.getPlateColor(), "未指定车牌颜色时默认蓝牌");
        verify(vehicleStatsMetrics).onBind();
    }

    @Test
    @DisplayName("绑定车辆：审计详情只出现脱敏车牌，不落明文")
    void bindVehicle_auditUsesMaskedPlate() {
        Vehicle input = new Vehicle();
        input.setPlateNo(PLAIN_PLATE);
        input.setOwnerId(OWNER_ID);

        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.insert(any(Vehicle.class))).thenReturn(1);

        vehicleService.bindVehicle(input, String.valueOf(OWNER_ID), "0");

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(auditClient).record(eq(OWNER_ID), isNull(), eq("绑定车辆"), eq("POST /api/vehicle/bind"),
                params.capture(), eq(true));
        String auditJson = String.valueOf(params.getValue());
        assertFalse(auditJson.contains(PLAIN_PLATE), "审计日志不得记录明文车牌：" + auditJson);
        assertTrue(auditJson.contains(AesUtils.maskPlate(PLAIN_PLATE)), "审计应记录脱敏车牌：" + auditJson);
    }

    @Test
    @DisplayName("绑定车辆：车牌重复抛 BizException(DATA_DUPLICATE)，不写库不审计")
    void bindVehicle_duplicatePlate_throwsBizException() {
        Vehicle input = new Vehicle();
        input.setPlateNo(PLAIN_PLATE);
        input.setOwnerId(OWNER_ID);

        when(vehicleMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> vehicleService.bindVehicle(input, String.valueOf(OWNER_ID), "0"));

        assertEquals(ErrorCode.DATA_DUPLICATE.getCode(), ex.getCode(), "重复车牌应返回 409 业务码");
        verify(vehicleMapper, never()).insert(any(Vehicle.class));
        verifyNoInteractions(vehicleStatsMetrics);
    }

    @Test
    @DisplayName("我的车辆：列表返回明文 + 脱敏值，空车牌不抛异常")
    void listByUserId_returnsPlainAndMasked() {
        Vehicle encrypted = new Vehicle();
        encrypted.setPlateNo(AesUtils.encrypt(PLAIN_PLATE));
        encrypted.setOwnerId(OWNER_ID);

        Vehicle blank = new Vehicle();
        blank.setOwnerId(OWNER_ID);

        when(vehicleMapper.selectList(any())).thenReturn(List.of(encrypted, blank));

        List<Vehicle> vehicles = vehicleService.listByUserId(OWNER_ID);

        assertEquals(PLAIN_PLATE, vehicles.get(0).getPlateNo(), "列表必须解密后返回明文");
        assertEquals(AesUtils.maskPlate(PLAIN_PLATE), vehicles.get(0).getPlateNoMasked());
        assertEquals(null, vehicles.get(1).getPlateNo(), "空车牌保持原样");
    }

    @Test
    @DisplayName("解绑车辆：非本人且非管理员返回 FORBIDDEN，不删除记录")
    void unbindVehicle_notOwner_throwsForbidden() {
        Vehicle existing = new Vehicle();
        existing.setPlateNo(AesUtils.encrypt(PLAIN_PLATE));
        existing.setOwnerId(OWNER_ID);

        when(vehicleMapper.selectById(anyLong())).thenReturn(existing);

        BizException ex = assertThrows(BizException.class,
                () -> vehicleService.unbindVehicle(9L, 2002L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(vehicleMapper, never()).deleteById(anyLong());
        verify(auditClient, never()).record(any(), any(), anyString(), anyString(), any(), eq(true));
    }

    @Test
    @DisplayName("绑定车辆：请求体伪造月卡字段与归属人无效（报告 P0-4）")
    void bindVehicle_ignoresForgedMonthlyFields() {
        Vehicle input = new Vehicle();
        input.setPlateNo(PLAIN_PLATE);
        input.setOwnerId(9999L);              // 伪造归属
        input.setType(2);                     // 伪造月租车
        input.setMonthlyExpire(LocalDateTime.now().plusYears(50));

        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        AtomicReference<Vehicle> saved = new AtomicReference<>();
        when(vehicleMapper.insert(any(Vehicle.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });

        vehicleService.bindVehicle(input, String.valueOf(OWNER_ID), "0");

        assertEquals(0, saved.get().getType(), "绑定时不得由前端指定为月租车，否则绕过月卡付费");
        assertNull(saved.get().getMonthlyExpire(), "绑定时不得由前端指定月卡有效期");
        assertEquals(OWNER_ID, saved.get().getOwnerId(), "业主绑车归属必须强制为登录人");
    }

    @Test
    @DisplayName("解绑车辆：车辆不存在返回 VEHICLE_NOT_FOUND")
    void unbindVehicle_missing_throwsNotFound() {
        when(vehicleMapper.selectById(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> vehicleService.unbindVehicle(9L, OWNER_ID));

        assertEquals(ErrorCode.VEHICLE_NOT_FOUND.getCode(), ex.getCode());
    }
}

package com.smartparking.payment;

import com.smartparking.payment.controller.PaymentRecordController;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.service.PaymentRecordService;
import com.smartparking.payment.task.PaymentTimeoutTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付回调验签与模拟通道收口测试（报告 P0-2 的回归闸门）。
 *
 * <p>历史问题：回调裸解析 payNo 即置成功，任何人 POST 一个 payNo 就能"0 元支付"；
 * mockPay 也不校验流水归属。这里锁死三件事：无签/错签一律 FAIL 且不落库、
 * 正确签名才放行、业主不能支付他人流水。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackSignatureTest {

    private static final String SECRET = "unit-test-secret";
    private static final String PAY_NO = "PAY202611181200001234";

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private PaymentTimeoutTask paymentTimeoutTask;

    @InjectMocks
    private PaymentRecordController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "callbackSecret", SECRET);
        ReflectionTestUtils.setField(controller, "mockEnabled", true);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("回调缺少签名：返回 FAIL，且不置支付成功")
    void callback_withoutSign_isRejected() throws Exception {
        mockMvc.perform(post("/api/payment/callback/wechat")
                        .contentType("text/plain")
                        .content("payNo=" + PAY_NO))
                .andExpect(status().isOk())
                .andExpect(content().string("FAIL"));

        verifyNoInteractions(paymentRecordService);
    }

    @Test
    @DisplayName("回调签名错误：返回 FAIL，且不置支付成功")
    void callback_withWrongSign_isRejected() throws Exception {
        mockMvc.perform(post("/api/payment/callback/alipay")
                        .contentType("text/plain")
                        .content("payNo=" + PAY_NO + "&sign=deadbeef"))
                .andExpect(status().isOk())
                .andExpect(content().string("FAIL"));

        verify(paymentRecordService, never()).paymentSuccess(anyString(), anyString());
    }

    @Test
    @DisplayName("签名正确：置支付成功并返回 SUCCESS")
    void callback_withValidSign_succeeds() throws Exception {
        String sign = DigestUtils.md5DigestAsHex((PAY_NO + "&" + SECRET).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/payment/callback/wechat")
                        .contentType("text/plain")
                        .content("payNo=" + PAY_NO + "&sign=" + sign + "&transactionId=WX123"))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        verify(paymentRecordService).paymentSuccess(PAY_NO, "WX123");
    }

    @Test
    @DisplayName("业主不能模拟支付他人流水（水平越权）")
    void mockPay_otherUsersRecord_forbidden() throws Exception {
        PaymentRecord record = new PaymentRecord();
        record.setPayNo(PAY_NO);
        record.setUserId(1001L);
        record.setPayStatus(0);
        record.setAmount(new BigDecimal("5.00"));
        when(paymentRecordService.getByPayNo(PAY_NO)).thenReturn(record);

        mockMvc.perform(post("/api/payment/wechat/pay")
                        .param("payNo", PAY_NO)
                        .header("X-User-Id", "2002")
                        .header("X-User-Role", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(paymentRecordService, never()).paymentSuccess(anyString(), anyString());
    }
}

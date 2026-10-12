package com.smartparking.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.query.PaymentRecordQuery;
import com.smartparking.payment.service.PaymentRecordService;
import com.smartparking.payment.task.PaymentTimeoutTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 */
@Slf4j
@Tag(name = "支付管理", description = "支付记录、账单管理")
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;
    private final PaymentTimeoutTask paymentTimeoutTask;

    @Operation(summary = "创建支付订单")
    @PostMapping("/create")
    public Result<PaymentRecord> create(
            @Parameter(description = "订单ID") @RequestParam Long orderId,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "支付金额") @RequestParam BigDecimal amount,
            @Parameter(description = "支付方式（1微信 2支付宝 3现金）") @RequestParam Integer payType,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主(role=0)只能给自己的订单发起支付，忽略前端传入的 userId
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        PaymentRecord record = paymentRecordService.createPayment(orderId, userId, amount, payType);
        return Result.success(record);
    }

    @Operation(summary = "微信支付（模拟：扫码即支付成功）")
    @PostMapping("/wechat/pay")
    public Result<Map<String, Object>> wechatPay(
            @Parameter(description = "支付流水号") @RequestParam String payNo) {
        return mockPay(payNo, "WX");
    }

    @Operation(summary = "支付宝支付（模拟：扫码即支付成功）")
    @PostMapping("/alipay/pay")
    public Result<Map<String, Object>> alipayPay(
            @Parameter(description = "支付流水号") @RequestParam String payNo) {
        return mockPay(payNo, "ZFB");
    }

    @Operation(summary = "微信支付回调（模拟）")
    @PostMapping("/callback/wechat")
    public String wechatCallback(@RequestBody String body) {
        // 模拟解析微信回调通知：payNo=xxx&transactionId=xxx
        Map<String, String> params = parseFormBody(body);
        String payNo = params.get("payNo");
        if (payNo != null) {
            try {
                String transactionId = params.getOrDefault("transactionId", "WX" + System.currentTimeMillis());
                paymentRecordService.paymentSuccess(payNo, transactionId);
            } catch (Exception e) {
                log.error("微信回调处理失败：{}", e.getMessage(), e);
            }
        }
        return "SUCCESS";
    }

    @Operation(summary = "支付宝支付回调（模拟）")
    @PostMapping("/callback/alipay")
    public String alipayCallback(@RequestBody String body) {
        Map<String, String> params = parseFormBody(body);
        String payNo = params.get("payNo");
        if (payNo != null) {
            try {
                String transactionId = params.getOrDefault("transactionId", "ZFB" + System.currentTimeMillis());
                paymentRecordService.paymentSuccess(payNo, transactionId);
            } catch (Exception e) {
                log.error("支付宝回调处理失败：{}", e.getMessage(), e);
            }
        }
        return "success";
    }

    /**
     * 模拟支付：直接置为支付成功，返回支付参数
     */
    private Result<Map<String, Object>> mockPay(String payNo, String channelPrefix) {
        PaymentRecord record = paymentRecordService.getByPayNo(payNo);
        if (record == null) {
            return Result.fail("支付记录不存在");
        }
        if (record.getPayStatus() != 0) {
            return Result.fail("支付状态异常，当前状态：" + record.getPayStatus());
        }

        String transactionId = channelPrefix + System.currentTimeMillis();
        paymentRecordService.paymentSuccess(payNo, transactionId);

        Map<String, Object> result = new HashMap<>();
        result.put("payNo", payNo);
        result.put("transactionId", transactionId);
        result.put("payStatus", 1);
        result.put("payType", record.getPayType());
        result.put("amount", record.getAmount());
        result.put("message", "模拟支付成功");
        return Result.success(result);
    }

    /**
     * 简单解析 form 表单体（key=value&key2=value2）
     */
    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) {
            return params;
        }
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return params;
    }

    @Operation(summary = "分页查询支付记录")
    @GetMapping("/list")
    public Result<PageResult<PaymentRecord>> list(PaymentRecordQuery query,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主(role=0)只能看自己的支付记录，忽略前端传入的 userId
        if ("0".equals(currentRole) && currentUserId != null) {
            query.setUserId(Long.valueOf(currentUserId));
        }
        Page<PaymentRecord> page = new Page<>(query.getCurrent(), query.getSize());
        Page<PaymentRecord> result = paymentRecordService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "查询订单支付记录")
    @GetMapping("/bill")
    public Result<PaymentRecord> bill(
            @Parameter(description = "订单ID") @RequestParam Long orderId) {
        PaymentRecord record = paymentRecordService.getByOrderId(orderId);
        return Result.success(record);
    }

    // ==================== 内部接口 ====================

    @Operation(summary = "支付成功回调（内部接口）")
    @PostMapping("/internal/success")
    public Result<Void> paymentSuccess(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @Parameter(description = "第三方交易号") @RequestParam String transactionId) {
        paymentRecordService.paymentSuccess(payNo, transactionId);
        return Result.success();
    }

    @Operation(summary = "退款（内部接口）")
    @PostMapping("/internal/refund")
    public Result<Void> refund(@Parameter(description = "支付流水号") @RequestParam String payNo) {
        paymentRecordService.refund(payNo);
        return Result.success();
    }

    @Operation(summary = "手动触发支付超时扫描（内部接口，演示/运维用）")
    @PostMapping("/internal/timeout-scan")
    public Result<Map<String, Object>> timeoutScan() {
        paymentTimeoutTask.scanNow();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "支付超时扫描已执行");
        return Result.success(result);
    }

    @Operation(summary = "指定日期范围已支付金额合计（内部接口，统计服务使用）")
    @GetMapping("/internal/income-summary")
    public Result<Map<String, Object>> incomeSummary(
            @Parameter(description = "开始日期 yyyy-MM-dd") @RequestParam String startDate,
            @Parameter(description = "结束日期 yyyy-MM-dd") @RequestParam String endDate) {
        java.time.LocalDate start = java.time.LocalDate.parse(startDate);
        java.time.LocalDate end = java.time.LocalDate.parse(endDate);
        BigDecimal total = paymentRecordService.sumPaidAmount(start.atStartOfDay(), end.atTime(java.time.LocalTime.MAX));
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return Result.success(result);
    }
}

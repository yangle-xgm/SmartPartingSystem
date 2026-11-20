package com.smartparking.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.query.PaymentRecordQuery;
import com.smartparking.payment.service.PaymentRecordService;
import com.smartparking.payment.task.PaymentTimeoutTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    private final com.smartparking.payment.channel.alipay.AlipaySandboxService alipaySandboxService;

    /**
     * 回调共享密钥：报告 P0-2 —— 原回调裸解析 payNo 即置成功，任何人 POST 一个 payNo
     * 就能"0 元支付"。接入真实渠道前的最小收口：回调必须携带
     * {@code sign = MD5(payNo + "&" + secret)}，验签失败一律不置成功。
     * 真实微信/支付宝验签（RSA/证书链）在接入渠道时替换本方法即可。
     */
    @Value("${smart-parking.payment.callback-secret:" + Constants.DEFAULT_CALLBACK_SECRET + "}")
    private String callbackSecret;

    /** 模拟支付开关：接入真实渠道后置 false，模拟端点整体下线（报告 P0-2 建议） */
    @Value("${smart-parking.payment.mock-enabled:true}")
    private boolean mockEnabled;

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

    @Operation(summary = "创建月卡支付流水")
    @PostMapping("/monthly/create")
    public Result<PaymentRecord> createMonthly(
            @Parameter(description = "月卡订单ID") @RequestParam Long monthlyOrderId,
            @Parameter(description = "支付方式（1微信 2支付宝 3现金）") @RequestParam Integer payType,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主(role=0)只能为自己名下的月卡订单付款；金额与归属以车辆服务的订单为准
        Long userId = "0".equals(currentRole) && currentUserId != null
                ? Long.valueOf(currentUserId) : null;
        return Result.success(paymentRecordService.createMonthlyPayment(monthlyOrderId, userId, payType));
    }

    @Operation(summary = "微信支付（模拟：扫码即支付成功）")
    @PostMapping("/wechat/pay")
    public Result<Map<String, Object>> wechatPay(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        return mockPay(payNo, "WX", currentUserId, currentRole);
    }

    @Operation(summary = "支付宝支付（沙箱开启时拒绝模拟通道）")
    @PostMapping("/alipay/pay")
    public Result<Map<String, Object>> alipayPay(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 沙箱模式下不再允许"点一下就成功"，必须走真实渠道收银台（钱要真的被沙箱账户扣掉）。
        // 否则两条通道并存 = 留着一个免费的绕过口子。
        if (alipaySandboxService.isEnabled()) {
            return Result.fail(ErrorCode.PAY_STATUS_ERROR,
                    "支付宝已切换为沙箱渠道，请使用收银台支付（/api/payment/alipay/cashier）");
        }
        return mockPay(payNo, "ZFB", currentUserId, currentRole);
    }

    @Operation(summary = "微信支付回调（模拟，需验签）")
    @PostMapping("/callback/wechat")
    public String wechatCallback(@RequestBody String body) {
        return handleCallback(body, "WX");
    }

    @Operation(summary = "支付宝支付回调（模拟，需验签）")
    @PostMapping("/callback/alipay")
    public String alipayCallback(@RequestBody String body) {
        return handleCallback(body, "ZFB");
    }

    /**
     * 统一回调处理：验签 → 置成功。验签失败或处理异常都返回 FAIL，
     * 让渠道继续重试（返回 SUCCESS 会导致渠道不再重试、直接掉单）。
     */
    private String handleCallback(String body, String channelPrefix) {
        Map<String, String> params = parseFormBody(body);
        String payNo = params.get("payNo");
        String sign = params.get("sign");
        if (payNo == null || sign == null) {
            log.warn("[{}] 回调缺少 payNo/sign，拒绝处理", channelPrefix);
            return "FAIL";
        }
        if (!verifySign(payNo, sign)) {
            log.warn("[{}] 回调验签失败 payNo={}", channelPrefix, payNo);
            return "FAIL";
        }
        try {
            String transactionId = params.getOrDefault("transactionId", channelPrefix + System.currentTimeMillis());
            paymentRecordService.paymentSuccess(payNo, transactionId);
            return "SUCCESS";
        } catch (Exception e) {
            log.error("[{}] 回调处理失败 payNo={}：{}", channelPrefix, payNo, e.getMessage(), e);
            // 未处理成功不回 SUCCESS，避免渠道停止重试
            return "FAIL";
        }
    }

    /** sign = MD5(payNo + "&" + callbackSecret)，十六进制小写 */
    private boolean verifySign(String payNo, String sign) {
        String expected = DigestUtils.md5DigestAsHex(
                (payNo + "&" + callbackSecret).getBytes(StandardCharsets.UTF_8));
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), sign.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 模拟支付：直接置为支付成功，返回支付参数。
     * 报告 P0-2：补两道闸 —— ① mock-enabled=false 时整条模拟链路关闭；
     * ② 业主只能支付自己的流水（此前任意 payNo 都能置成功）。
     */
    private Result<Map<String, Object>> mockPay(String payNo, String channelPrefix,
                                                String currentUserId, String currentRole) {
        if (!mockEnabled) {
            return Result.fail(ErrorCode.PAY_STATUS_ERROR, "模拟支付通道已关闭，请使用真实支付渠道");
        }
        PaymentRecord record = paymentRecordService.getByPayNo(payNo);
        if (record == null) {
            return Result.fail(ErrorCode.PAY_NOT_FOUND);
        }
        if ("0".equals(currentRole) && currentUserId != null
                && !Long.valueOf(currentUserId).equals(record.getUserId())) {
            return Result.fail(ErrorCode.FORBIDDEN, "无权支付该笔流水");
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
            @Parameter(description = "订单ID") @RequestParam Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        PaymentRecord record = paymentRecordService.getByOrderId(orderId);
        // E2E 报告 P1-5：业主(role=0)只能看自己订单的账单，防遍历他人 orderId
        if (record != null && "0".equals(currentRole) && currentUserId != null
                && !Long.valueOf(currentUserId).equals(record.getUserId())) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND, "无权查看该账单");
        }
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

package com.smartparking.payment.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.channel.alipay.AlipaySandboxService;
import com.smartparking.payment.channel.alipay.AlipayTradeResult;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.service.PaymentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝沙箱渠道接口。
 *
 * <p>链路：创建支付流水（/api/payment/create）→ 本类下单（precreate/cashier）
 * → 用户在沙箱收银台付款 → 到账确认（异步通知 notify 或主动查询 query）→ 入账。
 *
 * <p>为什么同时提供"通知"和"查询"：
 * 异步通知需要公网可达的回调地址，本机开发/答辩演示通常没有。主动查询让整个闭环
 * 在 localhost 也能跑通，且生产环境同样用查询做掉单兜底，不是演示专用旁路。
 */
@Slf4j
@Tag(name = "支付-支付宝沙箱", description = "支付宝沙箱下单、查询、异步通知")
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class AlipaySandboxController {

    private final AlipaySandboxService alipaySandboxService;
    private final PaymentRecordService paymentRecordService;

    /** 沙箱 APPID：异步通知里要核对 app_id，防止把其它应用的通知当成本系统交易 */
    @Value("${smart-parking.alipay.app-id:}")
    private String configuredAppId;

    @Operation(summary = "查询各支付渠道当前模式（mock / alipay-sandbox）")
    @GetMapping("/channels")
    public Result<Map<String, Object>> channels() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wechat", channelInfo("mock", "微信支付无公开沙箱环境，毕业设计按模拟通道演示"));
        result.put("alipay", channelInfo(
                alipaySandboxService.isEnabled() ? "alipay-sandbox" : "mock",
                alipaySandboxService.isEnabled()
                        ? (alipaySandboxService.isReady() ? "支付宝沙箱已就绪" : "支付宝沙箱已开启但密钥未配置")
                        : "支付宝走模拟通道（未开启沙箱）"));
        // 前端据此决定：走"模拟支付按钮"还是"跳收银台 + 轮询查询"
        result.put("alipaySandboxReady", alipaySandboxService.isReady());
        result.put("alipayAppId", alipaySandboxService.isEnabled() ? configuredAppId : "");
        return Result.success(result);
    }

    @Operation(summary = "支付宝当面付预下单（返回二维码内容，供扫码）")
    @PostMapping("/alipay/precreate")
    public Result<Map<String, Object>> precreate(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        PaymentRecord record = requireOwnRecord(payNo, currentUserId, currentRole);
        String qrCode = alipaySandboxService.precreate(payNo, record.getAmount(), subject(record));
        Map<String, Object> data = new HashMap<>();
        data.put("payNo", payNo);
        data.put("qrCode", qrCode);
        data.put("amount", record.getAmount());
        data.put("mode", "alipay-sandbox");
        return Result.success(data);
    }

    @Operation(summary = "支付宝收银台下单（返回自动提交表单，前端写入新窗口跳转沙箱收银台）")
    @PostMapping("/alipay/cashier")
    public Result<Map<String, Object>> cashier(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @Parameter(description = "场景：pc 电脑网站支付 / h5 手机网站支付") @RequestParam(defaultValue = "pc") String scene,
            @Parameter(description = "支付完成后的回跳地址；不传则用配置的 return-url。仅接受本机/私有网段")
            @RequestParam(required = false) String returnUrl,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        PaymentRecord record = requireOwnRecord(payNo, currentUserId, currentRole);
        String form = alipaySandboxService.cashierForm(payNo, record.getAmount(), subject(record), scene, returnUrl);
        Map<String, Object> data = new HashMap<>();
        data.put("payNo", payNo);
        data.put("form", form);
        data.put("amount", record.getAmount());
        data.put("mode", "alipay-sandbox");
        return Result.success(data);
    }

    @Operation(summary = "主动查询支付宝交易并结算（无公网回调时的闭环手段，前端轮询调用）")
    @PostMapping("/alipay/query")
    public Result<Map<String, Object>> query(
            @Parameter(description = "支付流水号") @RequestParam String payNo,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        PaymentRecord record = requireOwnRecord(payNo, currentUserId, currentRole);

        AlipayTradeResult trade = alipaySandboxService.query(payNo);
        Map<String, Object> data = new HashMap<>();
        data.put("payNo", payNo);
        data.put("tradeStatus", trade.getTradeStatus());
        data.put("tradeNo", trade.getTradeNo());

        boolean settled = false;
        if (trade.isPaid()) {
            // 金额一致性校验在 service 层（settleFromChannel），不一致直接拒绝入账
            paymentRecordService.settleFromChannel(payNo, trade.getTradeNo(), trade.getTotalAmount());
            settled = true;
            log.info("[支付宝沙箱] 主动查询确认到账 payNo={}, tradeNo={}, amount={}",
                    payNo, trade.getTradeNo(), trade.getTotalAmount());
        }
        PaymentRecord latest = paymentRecordService.getByPayNo(payNo);
        data.put("settled", settled);
        data.put("payStatus", latest == null ? record.getPayStatus() : latest.getPayStatus());
        return Result.success(data);
    }

    /**
     * 支付宝异步通知（服务器 → 服务器）。
     *
     * <p>必须返回纯文本 {@code success}，否则支付宝会按 4m/10m/... 的策略反复重试；
     * 处理失败时返回 {@code failure} 让渠道继续重试，绝不"吞掉"。
     *
     * <p>安全要点：
     * <ol>
     *   <li>验签（支付宝公钥 + RSA2）—— 决定性的第一道闸；</li>
     *   <li>核对 app_id —— 防止拿别人的合法通知来打本系统；</li>
     *   <li>金额校验 —— 在 settleFromChannel 内比对本地应付金额；</li>
     *   <li>只认 TRADE_SUCCESS / TRADE_FINISHED，其余状态不置成功。</li>
     * </ol>
     */
    @Operation(summary = "支付宝异步通知（渠道回调，需验签）")
    @PostMapping(value = "/callback/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        String payNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        log.info("[支付宝沙箱] 收到异步通知 payNo={}, tradeStatus={}, tradeNo={}",
                payNo, tradeStatus, params.get("trade_no"));

        if (!alipaySandboxService.verifyNotify(params)) {
            log.warn("[支付宝沙箱] 异步通知验签失败，拒绝 payNo={}", payNo);
            return "failure";
        }
        String appId = params.get("app_id");
        if (configuredAppId != null && !configuredAppId.isBlank() && !configuredAppId.equals(appId)) {
            log.warn("[支付宝沙箱] 异步通知 app_id 不匹配（期望 {}，实际 {}），拒绝", configuredAppId, appId);
            return "failure";
        }
        if (payNo == null || payNo.isBlank()) {
            log.warn("[支付宝沙箱] 异步通知缺少 out_trade_no");
            return "failure";
        }
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            // WAIT_BUYER_PAY / TRADE_CLOSED 等：确认收到即可，不改本地账务
            log.info("[支付宝沙箱] 非成功状态通知，忽略入账 payNo={}, status={}", payNo, tradeStatus);
            return "success";
        }
        try {
            BigDecimal amount = params.get("total_amount") == null
                    ? null : new BigDecimal(params.get("total_amount"));
            paymentRecordService.settleFromChannel(payNo, params.get("trade_no"), amount);
            return "success";
        } catch (Exception e) {
            log.error("[支付宝沙箱] 异步通知入账失败 payNo={}：{}", payNo, e.getMessage(), e);
            return "failure";
        }
    }

    // ------------------------------------------------------------------

    private Map<String, Object> channelInfo(String mode, String desc) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode);
        info.put("desc", desc);
        return info;
    }

    private String subject(PaymentRecord record) {
        return "停车费-" + record.getPayNo();
    }

    /**
     * 取支付流水并做归属校验：业主(role=0)只能操作自己的流水，
     * 防止遍历 payNo 帮别人下单/查询（与 mockPay 同一套越权口径）。
     */
    private PaymentRecord requireOwnRecord(String payNo, String currentUserId, String currentRole) {
        PaymentRecord record = paymentRecordService.getByPayNo(payNo);
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        if ("0".equals(currentRole) && currentUserId != null
                && !Long.valueOf(currentUserId).equals(record.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该笔流水");
        }
        return record;
    }
}

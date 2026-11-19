package com.smartparking.payment.channel.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.config.AlipayClientConfig;
import com.smartparking.payment.config.AlipayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * 支付宝沙箱渠道客户端。
 *
 * <p>本类只负责"和支付宝说话"，不碰本地账务（不依赖 PaymentRecordService），
 * 避免与 {@code PaymentRecordServiceImpl}（退款需要反向调用渠道）形成循环依赖。
 *
 * <p>沙箱能力清单：
 * <ol>
 *   <li>{@code alipay.trade.precreate} 当面付预下单 → 二维码串（扫码支付）</li>
 *   <li>{@code alipay.trade.page.pay} 电脑网站支付 → 自动提交表单 HTML（收银台跳转）</li>
 *   <li>{@code alipay.trade.wap.pay} 手机网站支付 → 同上，H5 用</li>
 *   <li>{@code alipay.trade.query} 主动查询交易 → 本地无公网回调时的闭环手段</li>
 *   <li>{@code alipay.trade.refund} 原路退款</li>
 *   <li>{@code AlipaySignature.rsaCheckV1} 异步通知验签</li>
 * </ol>
 *
 * <p>为什么必须有"主动查询"：异步通知要求 notify_url 公网可达，本机开发/答辩演示
 * 拿不到公网地址。用户在沙箱收银台付款后，前端轮询 query 接口即可确认到账，
 * 逻辑与真实生产完全一致（生产里查询同样是掉单兜底的标准手段）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipaySandboxService {

    private final AlipayProperties properties;
    private final ObjectProvider<AlipayClient> clientProvider;

    /** 回跳地址长度上限，防止把超长串塞进签名原文 */
    private static final int RETURN_URL_MAX_LENGTH = 200;

    /**
     * 允许的回跳地址：http/https + 本机或私有网段（含端口、路径）。
     * 私有网段覆盖手机热点（192.168.x）、校园/家庭局域网（10.x）、USB 反向映射（127.0.0.1）。
     */
    private static final java.util.regex.Pattern RETURN_URL_PATTERN = java.util.regex.Pattern.compile(
            "^https?://(?:localhost|127\\.0\\.0\\.1|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
                    + "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})"
                    + "(?::\\d{1,5})?(/[^\\s\"'<>\\\\]*)?$");

    /** 总开关是否打开（enabled=true） */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 是否已完成密钥配置，可以真正发请求 */
    public boolean isReady() {
        return properties.isReady() && clientProvider.getIfAvailable() != null;
    }

    public String getAppId() {
        return properties.getAppId();
    }

    private AlipayClient requireClient() {
        AlipayClient client = clientProvider.getIfAvailable();
        if (!properties.isReady() || client == null) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR,
                    "支付宝沙箱未配置完整（app-id / private-key / alipay-public-key），请先在配置或环境变量中填写");
        }
        return client;
    }

    /**
     * 当面付预下单，返回二维码内容（客户用支付宝 App 扫码）。
     */
    public String precreate(String payNo, BigDecimal amount, String subject) {
        AlipayClient client = requireClient();
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setBizContent(bizContent(payNo, amount, subject));
        try {
            AlipayTradePrecreateResponse response = client.execute(request);
            if (!response.isSuccess() || response.getQrCode() == null) {
                throw channelError("预下单", response.getCode(), response.getSubCode(), response.getSubMsg());
            }
            log.info("[支付宝沙箱] 预下单成功 payNo={}, qrCode={}", payNo, response.getQrCode());
            return response.getQrCode();
        } catch (AlipayApiException e) {
            throw channelException("预下单", e);
        }
    }

    /**
     * 网页/手机网站支付：返回一段自动提交的 HTML 表单。
     * 前端拿到后写入新窗口即跳转沙箱收银台（无需二维码库）。
     *
     * @param scene pc（电脑网站支付） / h5（手机网站支付）
     */
    public String cashierForm(String payNo, BigDecimal amount, String subject, String scene) {
        return cashierForm(payNo, amount, subject, scene, null);
    }

    /**
     * 同 {@link #cashierForm(String, BigDecimal, String, String)}，但允许调用方指定支付完成后的回跳地址。
     *
     * <p>为什么需要这个参数：配置里的 return-url 是本机固定值（http://localhost:3001/#/orders），
     * 手机 H5 付款后支付宝会跳到手机上这个地址，而手机上的 localhost 指向手机自己，
     * 结果就是「付款成功但支付页异常退出、页面打不开」。前端把当前访问的 origin 传进来，
     * 手机用热点/USB 访问时就能正确跳回原页面。
     *
     * <p>只接受 http/https + 本机或私有网段，避免被当成开放跳转（open redirect）利用。
     */
    public String cashierForm(String payNo, BigDecimal amount, String subject, String scene, String returnUrl) {
        AlipayClient client = requireClient();
        String backUrl = resolveReturnUrl(returnUrl);
        try {
            if ("h5".equalsIgnoreCase(scene)) {
                AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
                request.setNotifyUrl(properties.getNotifyUrl());
                request.setReturnUrl(backUrl);
                // 手机网站支付：product_code 固定 QUICK_WAP_WAY，否则 INVALID_PARAMETER
                request.setBizContent(bizContent(payNo, amount, subject, "QUICK_WAP_WAY")
                        .replaceFirst("\\}$", ",\"quit_url\":\"" + backUrl + "\"}"));
                AlipayTradeWapPayResponse response = client.pageExecute(request);
                if (!response.isSuccess() || response.getBody() == null) {
                    throw channelError("手机网站支付下单", response.getCode(), response.getSubCode(), response.getSubMsg());
                }
                return response.getBody();
            }
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(properties.getNotifyUrl());
            request.setReturnUrl(backUrl);
            // 电脑网站支付：product_code 固定 FAST_INSTANT_TRADE_PAY
            request.setBizContent(bizContent(payNo, amount, subject, "FAST_INSTANT_TRADE_PAY"));
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess() || response.getBody() == null) {
                throw channelError("电脑网站支付下单", response.getCode(), response.getSubCode(), response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            throw channelException("收银台下单", e);
        }
    }

    /**
     * 回跳地址白名单校验：只允许 http/https + 本机/私有网段。
     * 不合法（含引号、外网域名、超长）时退回配置里的 return-url，不影响下单。
     */
    String resolveReturnUrl(String candidate) {
        if (candidate != null) {
            String value = candidate.trim();
            if (value.length() <= RETURN_URL_MAX_LENGTH && RETURN_URL_PATTERN.matcher(value).matches()) {
                return value;
            }
            log.warn("[支付宝沙箱] 忽略不合法的 returnUrl: {}", candidate);
        }
        return properties.getReturnUrl();
    }

    /**
     * 主动查询交易。渠道侧不存在该笔时返回 tradeStatus=null（不抛异常），
     * 让调用方按"用户还没付款"处理而不是报错。
     */
    public AlipayTradeResult query(String payNo) {
        AlipayClient client = requireClient();
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + payNo + "\"}");
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                // ACQ.TRADE_NOT_EXIST：用户还没扫码/还没付款，属正常态
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    return AlipayTradeResult.builder().tradeStatus("NOT_EXIST").raw(response.getBody()).build();
                }
                throw channelError("查询交易", response.getCode(), response.getSubCode(), response.getSubMsg());
            }
            BigDecimal total = response.getTotalAmount() == null ? null : new BigDecimal(response.getTotalAmount());
            return AlipayTradeResult.builder()
                    .tradeStatus(response.getTradeStatus())
                    .tradeNo(response.getTradeNo())
                    .totalAmount(total)
                    .raw(response.getBody())
                    .build();
        } catch (AlipayApiException e) {
            throw channelException("查询交易", e);
        }
    }

    /**
     * 原路退款（全额）。out_request_no 用 payNo-R1 保证同一笔退款在渠道侧幂等。
     */
    public void refund(String payNo, BigDecimal amount) {
        AlipayClient client = requireClient();
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent("{\"out_trade_no\":\"" + payNo + "\","
                + "\"refund_amount\":" + money(amount) + ","
                + "\"out_request_no\":\"" + payNo + "-R1\","
                + "\"refund_reason\":\"停车费退款\"}");
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (!response.isSuccess()) {
                throw channelError("退款", response.getCode(), response.getSubCode(), response.getSubMsg());
            }
            // fund_change=N 表示渠道侧该笔退款已处理过（幂等命中），同样视为成功
            log.info("[支付宝沙箱] 退款受理 payNo={}, amount={}, fundChange={}, tradeNo={}",
                    payNo, amount, response.getFundChange(), response.getTradeNo());
        } catch (AlipayApiException e) {
            throw channelException("退款", e);
        }
    }

    /**
     * 异步通知验签。注意：验签用的是**支付宝公钥**，且必须先把 sign / sign_type 剔出去，
     * 由 SDK 的 rsaCheckV1 内部处理（它会自行取 sign 并移出待签名串）。
     */
    public boolean verifyNotify(Map<String, String> params) {
        if (!properties.isReady()) {
            log.warn("[支付宝沙箱] 收到异步通知但本地未配置密钥，拒绝");
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(params, AlipayClientConfig.normalize(properties.getAlipayPublicKey()),
                    properties.getCharset(), properties.getSignType());
        } catch (AlipayApiException e) {
            log.warn("[支付宝沙箱] 异步通知验签异常：{}", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------

    private String bizContent(String payNo, BigDecimal amount, String subject) {
        return bizContent(payNo, amount, subject, null);
    }

    /**
     * 组装 biz_content。
     *
     * <p>两个容易被忽略的硬性要求（错了就是 {@code INVALID_PARAMETER}「订单信息无法识别」）：
     * <ol>
     *   <li>{@code total_amount} 必须是<b>字符串</b>（{@code "0.02"} 而不是 {@code 0.02}）——
     *       预下单接口对此宽容，网页/手机网站支付会直接拒；</li>
     *   <li>网页/手机网站支付必须带 {@code product_code}：
     *       PC = {@code FAST_INSTANT_TRADE_PAY}，H5 = {@code QUICK_WAP_WAY}。</li>
     * </ol>
     *
     * @param productCode 为 null 时不输出该字段（当面付 precreate 不需要）
     */
    private String bizContent(String payNo, BigDecimal amount, String subject, String productCode) {
        String safeSubject = subject == null || subject.isBlank()
                ? properties.getSubjectPrefix() : subject;
        StringBuilder sb = new StringBuilder("{\"out_trade_no\":\"").append(payNo).append("\",")
                .append("\"total_amount\":\"").append(money(amount)).append("\",")
                .append("\"subject\":\"").append(safeSubject.replace("\"", "")).append("\"");
        if (productCode != null && !productCode.isBlank()) {
            sb.append(",\"product_code\":\"").append(productCode).append("\"");
        }
        sb.append(",\"timeout_express\":\"").append(properties.getTimeoutMinutes()).append("m\"}");
        return sb.toString();
    }

    /** 渠道要求金额为两位小数的十进制字符串（不能带科学计数法/多余精度） */
    private String money(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BizException channelError(String action, String code, String subCode, String subMsg) {
        String detail = (subMsg == null ? "" : subMsg) + " [" + code + "/" + subCode + "]";
        log.warn("[支付宝沙箱] {}失败：{}", action, detail);
        return new BizException(ErrorCode.PAY_STATUS_ERROR, "支付宝" + action + "失败：" + detail);
    }

    private BizException channelException(String action, AlipayApiException e) {
        log.error("[支付宝沙箱] {}异常：{}", action, e.getMessage());
        return new BizException(ErrorCode.PAY_STATUS_ERROR, "支付宝" + action + "异常：" + e.getMessage());
    }
}

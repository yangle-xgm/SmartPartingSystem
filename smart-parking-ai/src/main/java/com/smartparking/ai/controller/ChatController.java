package com.smartparking.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.service.DoubaoChatService;
import com.smartparking.ai.monitor.AiCallMetrics;
import com.smartparking.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能客服：接入豆包（火山方舟）大模型 API，未配置或调用失败时降级本地关键词 mock。
 */
@Slf4j
@Tag(name = "AI智能客服", description = "自然语言问答（豆包大模型）")
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    @Value("${ai.chat.provider:mock}")
    private String provider;

    @Value("${ai.chat.doubao-endpoint-id:}")
    private String doubaoEndpointId;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DoubaoChatService doubaoChatService;
    private final AiCallMetrics aiCallMetrics;

    @Operation(summary = "智能客服问答")
    @PostMapping
    public Result<Map<String, String>> chat(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        List<Map<String, String>> history = extractHistory(body);

        String answer;
        String effectiveProvider = provider;
        if ("doubao".equals(provider) && doubaoChatService.isConfigured()) {
            try {
                answer = doubaoChatService.chat(history, question);
                aiCallMetrics.onRealCall("chat");
            } catch (Exception e) {
                log.warn("豆包调用失败，降级本地 mock: {}", e.getMessage());
                answer = mockAnswer(question);
                aiCallMetrics.onMockFallback("chat");
                effectiveProvider = "mock(fallback)";
            }
        } else {
            answer = mockAnswer(question);
            aiCallMetrics.onMockFallback("chat");
        }
        return Result.success(Map.of(
                "answer", answer,
                "provider", effectiveProvider,
                "model", "doubao".equals(provider) ? doubaoEndpointId : "local-mock"
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractHistory(Map<String, Object> body) {
        Object raw = body.get("history");
        if (raw instanceof List) {
            try {
                return MAPPER.convertValue(raw,
                        MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private String mockAnswer(String q) {
        if (q.contains("停车") || q.contains("费用") || q.contains("缴费")) {
            return "您可以在【缴费管理】页面查看待支付订单，支持微信/支付宝在线支付。出场前完成支付即可自动抬杆。";
        }
        if (q.contains("访客") || q.contains("邀请码")) {
            return "进入【车辆管理】点击【邀请访客】，填写访客车牌号和有效期，系统会生成6位邀请码。访客到岗亭输入邀请码即可入场。";
        }
        if (q.contains("车位") || q.contains("预约")) {
            return "在【车位管理】页面可查看实时空闲车位，点击【预约】可锁定车位30分钟，超时未入场自动释放。";
        }
        if (q.contains("月卡") || q.contains("续费")) {
            return "在【车辆管理】找到您的月卡车，点击【续费】按钮可延长一个月有效期。月卡到期前7天会推送提醒。";
        }
        if (q.contains("黑名单")) {
            return "黑名单由物业管理员操作，被列入黑名单的车辆无法入场。如有疑问请联系物业前台。";
        }
        return "您好，我是智慧停车助手。您可以问我：如何缴费、如何邀请访客、如何预约车位、月卡怎么续费等问题。";
    }
}

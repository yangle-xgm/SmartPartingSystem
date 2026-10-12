package com.smartparking.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 智能客服：接入豆包大模型API
 */
@Slf4j
@Tag(name = "AI智能客服", description = "自然语言问答（豆包大模型）")
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    @Value("${ai.chat.provider:mock}")
    private String provider;

    @Value("${ai.chat.doubao-api-key:}")
    private String doubaoApiKey;

    @Value("${ai.chat.doubao-model:doubao-pro-4k}")
    private String doubaoModel;

    private static final String DOUBAO_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Operation(summary = "智能客服问答")
    @PostMapping
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        String answer;
        if ("doubao".equals(provider) && doubaoApiKey != null && !doubaoApiKey.isEmpty()) {
            try {
                answer = callDoubao(question);
            } catch (Exception e) {
                log.warn("豆包API调用失败，降级到本地: {}", e.getMessage());
                answer = mockAnswer(question);
            }
        } else {
            answer = mockAnswer(question);
        }
        return Result.success(Map.of("answer", answer, "provider", provider));
    }

    private String callDoubao(String question) throws Exception {
        Map<String, Object> sysMsg = Map.of("role", "system", "content",
                "你是小区智慧停车系统的智能客服助手。请简洁回答用户关于停车缴费、访客邀请、车位预约、月卡续费、车辆管理等问题。回答不超过100字。");
        Map<String, Object> userMsg = Map.of("role", "user", "content", question);
        Map<String, Object> reqBody = Map.of(
                "model", doubaoModel,
                "messages", List.of(sysMsg, userMsg),
                "temperature", 0.7,
                "max_tokens", 300
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoApiKey);
        ResponseEntity<String> resp = restTemplate.exchange(DOUBAO_URL, HttpMethod.POST,
                new HttpEntity<>(MAPPER.writeValueAsString(reqBody), headers), String.class);
        JsonNode root = MAPPER.readTree(resp.getBody());
        return root.path("choices").path(0).path("message").path("content").asText();
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

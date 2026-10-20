package com.smartparking.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆包（火山方舟 Ark）智能客服真实接入。
 *
 * <p>火山方舟兼容 OpenAI 接口：POST /api/v3/chat/completions。
 * 关键点：请求体的 {@code model} 字段必须填「推理接入点 ID」（格式 {@code ep-xxxx}），
 * 不是模型名（如 doubao-pro-4k），否则会返回 invalid_model 错误。接入点需在
 * console.volcengine.com/ark 的「在线推理」中创建后复制其 ID。</p>
 *
 * <p>任何网络/鉴权/解析失败都会抛 {@link RuntimeException}，供 Controller 降级到本地 mock，
 * 保证未配置 Key 或豆包不可达时系统仍可应答。</p>
 */
@Slf4j
@Service
public class DoubaoChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是小区智慧停车系统的智能客服助手。请简洁、准确地回答用户关于停车缴费、访客邀请、" +
                    "车位预约、月卡续费、车辆管理、黑名单等问题。回答不超过100字，不使用 Markdown 复杂排版。";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String endpointId;
    private final String baseUrl;
    private final String systemPrompt;

    public DoubaoChatService(
            @Value("${ai.chat.doubao-api-key:}") String apiKey,
            @Value("${ai.chat.doubao-endpoint-id:}") String endpointId,
            @Value("${ai.chat.doubao-base-url:https://ark.cn-beijing.volces.com/api/v3/chat/completions}") String baseUrl) {
        this(buildRestTemplate(), apiKey, endpointId, baseUrl, DEFAULT_SYSTEM_PROMPT);
    }

    /** 包级构造函数，供单测注入自定义 RestTemplate / baseUrl（指向本地 MockServer）。 */
    DoubaoChatService(RestTemplate restTemplate, String apiKey, String endpointId, String baseUrl, String systemPrompt) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.endpointId = endpointId;
        this.baseUrl = baseUrl;
        this.systemPrompt = systemPrompt;
    }

    /** Key 与推理接入点 ID 齐备才认为已配置，可走真实模型。 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && endpointId != null && !endpointId.isBlank();
    }

    /** 单轮问答。 */
    public String chat(String question) {
        return chat(List.of(), question);
    }

    /**
     * 多轮问答。
     *
     * @param history 历史对话（每项含 role/content），可为空
     * @param question 当前用户问题
     * @return 模型回答文本
     * @throws RuntimeException 调用或解析失败时抛出，供上层降级
     */
    public String chat(List<Map<String, String>> history, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.getOrDefault("role", "user");
                String content = h.get("content");
                if (content != null && !content.isBlank()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", question));

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("model", endpointId);
        reqBody.put("messages", messages);
        reqBody.put("temperature", 0.7);
        reqBody.put("max_tokens", 300);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl, HttpMethod.POST,
                    new HttpEntity<>(MAPPER.writeValueAsString(reqBody), headers),
                    String.class);
            return parseAnswer(resp.getBody());
        } catch (HttpStatusCodeException e) {
            log.warn("豆包接口返回非 2xx: {} | body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("豆包接口调用失败: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.warn("豆包接口调用异常: {}", e.getMessage());
            throw new RuntimeException("豆包接口调用异常: " + e.getMessage(), e);
        }
    }

    /** 解析 Ark 返回 JSON，提取 choices[0].message.content；空 choices 或含 error 字段则抛异常。 */
    String parseAnswer(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String msg = error.path("message").asText(error.path("code").asText("unknown error"));
                throw new RuntimeException("豆包返回错误: " + msg);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("豆包返回为空（无 choices）");
            }
            return choices.path(0).path("message").path("content").asText().trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("豆包返回解析失败: " + e.getMessage(), e);
        }
    }

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.getMessageConverters().stream()
                .filter(c -> c instanceof StringHttpMessageConverter)
                .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
        return rt;
    }
}

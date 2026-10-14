package com.smartparking.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 百度车牌识别真实接入。
 *
 * <p>调用流程：先用 API Key / Secret Key 换 {@code access_token}（带内存缓存，临近过期自动刷新），
 * 再把图片 base64 后 POST 到车牌识别接口，解析返回的 {@code words_result} 得到车牌号、颜色与置信度。
 *
 * <p>本类不引入任何新依赖，复用 Spring 的 {@link RestTemplate} 与 Jackson。
 * 任何网络/解析失败都向上抛 {@link RuntimeException}，由调用方（PlateController）降级到 mock，
 * 从而保证「未配置密钥 / 百度不可达」时系统仍可运行。
 */
public class BaiduPlateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String secretKey;
    private final String tokenUrl;
    private final String recognizeUrl;
    private final RestTemplate restTemplate;

    /** access_token 缓存（单例服务内复用，百度 token 默认约 30 天有效） */
    private volatile String cachedToken;
    private volatile long tokenExpireAt; // epoch millis

    public BaiduPlateService(String apiKey, String secretKey, String tokenUrl, String recognizeUrl, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.tokenUrl = tokenUrl;
        this.recognizeUrl = recognizeUrl;
        this.restTemplate = restTemplate;
    }

    /** 是否已配置密钥（用于决定走真实识别还是 mock） */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    /**
     * 识别图片中的车牌。
     *
     * @param imageBytes 原始图片字节（jpg/jpeg/png/bmp，base64+urlencode 后 ≤8M）
     * @return 识别结果（车牌号 / 颜色 / 0-100 置信度）
     * @throws RuntimeException 鉴权失败、网络异常、未识别到车牌或响应解析失败时抛出
     */
    public PlateRecognition recognize(byte[] imageBytes) {
        String token = getAccessToken();
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("image", base64);
        body.add("multi_detect", "true");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        String url = recognizeUrl + "?access_token=" + token;
        String resp = restTemplate.postForObject(url, entity, String.class);
        if (resp == null) {
            throw new IllegalStateException("百度车牌识别返回空响应");
        }
        return parseResponse(resp);
    }

    private String getAccessToken() {
        long now = System.currentTimeMillis();
        String cached = cachedToken;
        if (cached != null && now < tokenExpireAt) {
            return cached;
        }
        synchronized (this) {
            if (cachedToken != null && now < tokenExpireAt) {
                return cachedToken;
            }
            String url = tokenUrl + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
            try {
                String resp = restTemplate.getForObject(url, String.class);
                if (resp == null) {
                    throw new IllegalStateException("获取百度 access_token 返回空响应");
                }
                JsonNode node = MAPPER.readTree(resp);
                if (node.has("error")) {
                    throw new IllegalStateException("获取百度 access_token 失败: " + node.path("error_description").asText());
                }
                String token = node.path("access_token").asText();
                if (token.isBlank()) {
                    throw new IllegalStateException("获取百度 access_token 失败: 响应中无 access_token");
                }
                int expiresIn = node.path("expires_in").asInt(2592000);
                cachedToken = token;
                // 提前 60s 过期，避免临界时刻用到失效 token
                tokenExpireAt = System.currentTimeMillis() + (expiresIn - 60L) * 1000L;
                return token;
            } catch (HttpStatusCodeException e) {
                throw new IllegalStateException("获取百度 access_token HTTP 错误: " + e.getStatusCode(), e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("获取百度 access_token 异常: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 解析百度车牌识别响应。同时兼容两种返回形态：
     * <ul>
     *   <li>新版（产品页 demo）：{@code words_result: [ { number, color, probability:[...] } ]}</li>
     *   <li>旧版（部分文档）：{@code words_result: { number, color, probability:[...] }, words_result_num: N }</li>
     * </ul>
     * 置信度取 {@code probability} 数组的最小值 ×100（最保守，单字符不确定即整体降权），
     * 与下游 vehicle 模块 0-100 尺度、<80 判模糊的约定一致。
     */
    static PlateRecognition parseResponse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.has("error_code")) {
                throw new IllegalArgumentException("百度 OCR 错误 " + root.path("error_code").asText()
                        + ": " + root.path("error_msg").asText());
            }
            JsonNode wr = root.path("words_result");
            JsonNode first;
            if (wr.isArray()) {
                first = wr.size() > 0 ? wr.get(0) : null;
            } else {
                first = wr;
            }
            if (first == null || first.isMissingNode() || first.isNull() || first.isEmpty()) {
                throw new IllegalArgumentException("百度未识别到车牌");
            }
            String number = first.path("number").asText("");
            String color = first.path("color").asText("");
            double confidence = 0.0;
            JsonNode prob = first.path("probability");
            if (prob.isArray() && prob.size() > 0) {
                double min = 1.0;
                for (JsonNode p : prob) {
                    min = Math.min(min, p.asDouble(1.0));
                }
                confidence = min * 100.0;
            }
            return new PlateRecognition(number, color, Math.round(confidence * 100.0) / 100.0);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析百度响应失败: " + e.getMessage(), e);
        }
    }
}

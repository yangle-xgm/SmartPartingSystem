package com.smartparking.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class DoubaoChatServiceParseTest {

    private DoubaoChatService newService(String apiKey, String endpointId) {
        return new DoubaoChatService(new RestTemplate(), apiKey, endpointId, "http://localhost/x", "sys");
    }

    @Test
    void isConfigured_requiresKeyAndEndpointId() {
        assertTrue(newService("key", "ep-1").isConfigured());
        assertFalse(newService("", "ep-1").isConfigured());
        assertFalse(newService("key", "").isConfigured());
        assertFalse(newService(null, "ep-1").isConfigured());
    }

    @Test
    void parseAnswer_normal() {
        String json = "{\"choices\":[{\"message\":{\"content\":\" 您好，已为您查询 \"}}]}";
        assertEquals("您好，已为您查询", newService("k", "ep").parseAnswer(json));
    }

    @Test
    void parseAnswer_emptyChoices_throws() {
        String json = "{\"choices\":[]}";
        assertThrows(RuntimeException.class, () -> newService("k", "ep").parseAnswer(json));
    }

    @Test
    void parseAnswer_errorField_throwsWithMessage() {
        String json = "{\"error\":{\"message\":\"invalid api key\"}}";
        RuntimeException ex = assertThrows(RuntimeException.class, () -> newService("k", "ep").parseAnswer(json));
        assertTrue(ex.getMessage().contains("invalid api key"));
    }
}

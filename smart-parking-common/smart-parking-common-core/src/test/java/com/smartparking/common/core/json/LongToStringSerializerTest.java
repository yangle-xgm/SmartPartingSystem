package com.smartparking.common.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端功能测试 DEF-01：19 位雪花主键必须序列化为字符串，否则浏览器 JSON.parse 丢精度。
 */
class LongToStringSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new SimpleModule()
                    .addSerializer(Long.class, LongToStringSerializer.INSTANCE)
                    .addSerializer(Long.TYPE, LongToStringSerializer.INSTANCE));

    @Test
    @DisplayName("雪花 id 序列化为字符串，避免 JS 精度截断")
    void snowflakeId_serializedAsString() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 2115696681016836097L);

        String json = mapper.writeValueAsString(body);

        assertEquals("{\"id\":\"2115696681016836097\"}", json);
    }

    @Test
    @DisplayName("业务计数等安全范围内的 Long 仍是数字，前端分页/统计逻辑不受影响")
    void smallLong_staysNumber() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", 87L);
        body.put("currentParked", 0L);

        String json = mapper.writeValueAsString(body);

        assertEquals("{\"total\":87,\"currentParked\":0}", json);
    }

    @Test
    @DisplayName("负数大整数同样转字符串")
    void negativeSnowflake_serializedAsString() throws Exception {
        assertTrue(mapper.writeValueAsString(-2115696681016836097L).contains("\"-2115696681016836097\""));
    }

    @Test
    @DisplayName("字符串形式的 id 可以反序列化回 Long（写接口无需改造）")
    void stringId_deserializesToLong() throws Exception {
        Long id = mapper.readValue("\"2115696681016836097\"", Long.class);
        assertEquals(2115696681016836097L, id);
    }
}

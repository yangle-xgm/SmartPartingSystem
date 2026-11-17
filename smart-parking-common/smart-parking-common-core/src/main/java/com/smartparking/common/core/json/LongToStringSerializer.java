package com.smartparking.common.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 大整数 Long 序列化器：超出 JS 安全整数范围（2^53-1）时序列化为字符串。
 *
 * <p>背景（前端功能测试 DEF-01）：雪花主键为 19 位，浏览器 {@code JSON.parse} 会把
 * {@code 2115696681016836097} 变成 {@code 2115696681016836000}，前端再拿这个 id 调
 * 详情/编辑/删除接口全部落空，表现为"提示成功但没生效"。</p>
 *
 * <p>只对超出安全范围的值转字符串，业务计数（total、currentParked 等）仍是数字，
 * 前端分页、统计等数值逻辑不受影响；Jackson 反序列化默认支持 String → Long，写接口无需改动。</p>
 */
public class LongToStringSerializer extends JsonSerializer<Long> {

    /** JS Number.MAX_SAFE_INTEGER */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    public static final LongToStringSerializer INSTANCE = new LongToStringSerializer();

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value);
        }
    }
}

package com.smartparking.common.core.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.smartparking.common.core.json.LongToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 *
 * <p>前端功能测试 DEF-01：雪花主键 19 位，直接以 JSON number 下发会被浏览器
 * {@code JSON.parse} 截断精度。这里对超出 JS 安全整数范围的 Long 统一序列化为字符串。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("longToStringModule");
            module.addSerializer(Long.class, LongToStringSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, LongToStringSerializer.INSTANCE);
            builder.modulesToInstall(module);
        };
    }
}

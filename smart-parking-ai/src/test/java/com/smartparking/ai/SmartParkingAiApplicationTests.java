package com.smartparking.ai;

import com.smartparking.ai.controller.ChatController;
import com.smartparking.ai.controller.PlateController;
import com.smartparking.ai.service.DoubaoChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文冒烟测试：加载完整 Spring 容器，验证所有 Bean（尤其是多构造器的
 * {@link DoubaoChatService}）能正常装配。仅靠纯单测（直接 new 测试构造器）
 * 无法发现「Spring 找不到可用构造器」这类装配错误——本测试即为该回归兜底。
 *
 * <p>CI 环境无 Nacos / MySQL 等中间件，故显式关闭 Nacos 的 config 与
 * discovery，仅校验应用自身 Bean 装配是否正确。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "server.port=0",
        "ai.plate.provider=mock",
        "ai.chat.provider=mock"
})
class SmartParkingAiApplicationTests {

    @Autowired
    private ChatController chatController;

    @Autowired
    private PlateController plateController;

    @Autowired
    private DoubaoChatService doubaoChatService;

    @Test
    void contextLoads() {
        assertThat(chatController).isNotNull();
        assertThat(plateController).isNotNull();
        assertThat(doubaoChatService).isNotNull();
        // 默认 mock 模式：未配置密钥，isConfigured 应为 false
        assertThat(doubaoChatService.isConfigured()).isFalse();
    }
}

package com.smartparking.user;

import com.smartparking.user.controller.AuditLogController;
import com.smartparking.user.controller.SysUserController;
import com.smartparking.user.handler.GlobalExceptionHandler;
import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户服务内部接口契约测试（provider 侧，standalone MockMvc 范式，不加载 Spring 上下文）。
 *
 * <p>覆盖的消费者：
 * <ul>
 *   <li>order / payment / vehicle 的 AuditClient.record() 拼 query 调 POST /api/audit/internal/record</li>
 *   <li>notify 的 NotificationController.fetchOwnerIds() 调 GET /api/user/internal/owner-ids
 *       （解析 resp.get("data") 为 List&lt;Number&gt; 再转 Long）</li>
 * </ul>
 *
 * <p>范式说明：主类带 @MapperScan，@WebMvcTest 会因缺少 SqlSessionFactory 起不来上下文（见 SysUserControllerTest 注释），
 * 故用 standaloneSetup 直接构造 Controller 并挂载 GlobalExceptionHandler，跨平台稳定。
 */
@ExtendWith(MockitoExtension.class)
class UserInternalApiContractTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SysUserService sysUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AuditLogController(auditLogService),
                        new SysUserController(sysUserService, auditLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/audit/internal/record：AuditClient 真实传参，返回 Result 成功契约且参数透传到 service")
    void recordAudit_auditClientParams_returnsResultSuccess() throws Exception {
        // 与 order/client/AuditClient.java record() 拼接的 URL 参数完全一致
        mockMvc.perform(post("/api/audit/internal/record")
                        .param("operation", "设备联动入场")
                        .param("method", "POST /api/order/internal/device-entry")
                        .param("isSuccess", "1")
                        .param("userId", "1001")
                        .param("username", "admin")
                        .param("params", "{\"plateNo\":\"京A12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(auditLogService).record(
                eq(1001L), eq("admin"), eq("设备联动入场"),
                eq("POST /api/order/internal/device-entry"), eq("{\"plateNo\":\"京A12345\"}"),
                eq(true), isNull(), isNull());
    }

    @Test
    @DisplayName("POST /api/audit/internal/record：isSuccess=0 与可选参数全缺省（AuditClient 最小传参）")
    void recordAudit_minimalParams_passesFalseFlag() throws Exception {
        // AuditClient 最小场景：仅 operation/method/isSuccess=0（失败审计）
        mockMvc.perform(post("/api/audit/internal/record")
                        .param("operation", "支付超时补偿")
                        .param("method", "SCHEDULED /payment-timeout")
                        .param("isSuccess", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(auditLogService).record(
                isNull(), nullable(String.class), eq("支付超时补偿"),
                eq("SCHEDULED /payment-timeout"), isNull(),
                eq(false), isNull(), isNull());
    }

    @Test
    @DisplayName("POST /api/audit/internal/record：缺必填 operation 时 GlobalExceptionHandler 转 Result.fail（消费者按 code!=200 降级）")
    void recordAudit_missingOperation_returnsFailContract() throws Exception {
        mockMvc.perform(post("/api/audit/internal/record")
                        .param("method", "POST /api/order/internal/device-entry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/user/internal/owner-ids：data 为 Long 数组（notify 广播按 Number 解析）")
    void ownerIds_returnsLongArray() throws Exception {
        when(sysUserService.listOwnerIds()).thenReturn(List.of(1L, 2L, 3L));

        mockMvc.perform(get("/api/user/internal/owner-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // notify fetchOwnerIds(): Long.valueOf(String.valueOf(o))，元素必须是数字
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value(1));

        verify(sysUserService).listOwnerIds();
    }
}

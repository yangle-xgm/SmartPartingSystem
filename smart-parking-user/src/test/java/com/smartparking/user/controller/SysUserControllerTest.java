package com.smartparking.user.controller;

import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import com.smartparking.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户模块控制器测试（纯 MockMvc standalone，不加载 Spring 上下文 / 不连 MyBatis / 不连 Nacos）。
 * 直接构造 Controller 并注入 Mock 依赖，验证登录/验证码/用户信息/刷新 Token 的 HTTP 映射与统一响应契约。
 *
 * 说明：项目主类带 @MapperScan，若用 @WebMvcTest 会自动扫描 MyBatis Mapper 并因缺少 SqlSessionFactory 而在
 * 干净构建（CI 的 `mvn clean`）下起不来上下文。改用 standaloneSetup 彻底规避，跨平台稳定。
 */
@ExtendWith(MockitoExtension.class)
class SysUserControllerTest {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SysUserController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_returnsTokens() throws Exception {
        when(sysUserService.login(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("accessToken", "at-xxx", "refreshToken", "rt-xxx"));

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\",\"captcha\":\"1\",\"captchaKey\":\"k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("at-xxx"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-xxx"));
    }

    @Test
    void captcha_isWhitelisted_noTokenRequired() throws Exception {
        when(sysUserService.generateCaptcha()).thenReturn(Map.of("captchaKey", "k", "captchaImg", "img"));

        mockMvc.perform(get("/api/user/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.captchaKey").value("k"));
    }

    @Test
    void getUserInfo_returnsUserVO() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        vo.setRole(2);
        when(sysUserService.getUserInfo(1L)).thenReturn(vo);

        mockMvc.perform(get("/api/user/info").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value(2));
    }

    @Test
    void refresh_returnsTokens() throws Exception {
        when(sysUserService.refreshToken(anyString()))
                .thenReturn(Map.of("accessToken", "at2", "refreshToken", "rt2"));

        mockMvc.perform(post("/api/user/refresh")
                        .header("Authorization", "Bearer rt-xxx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("at2"));
    }
}

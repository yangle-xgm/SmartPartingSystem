package com.smartparking.user.controller;

import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import com.smartparking.user.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户模块集成测试（Web 层切片）：验证登录/验证码/用户信息/刷新 Token 的 HTTP 映射与统一响应契约。
 * 不启动整个 Spring 上下文、不连 Nacos/Redis/MySQL，Service 全部 Mock。
 */
@WebMvcTest(controllers = SysUserController.class)
class SysUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysUserService sysUserService;

    @MockBean
    private AuditLogService auditLogService;

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

package com.smartparking.notify;

import com.smartparking.notify.controller.FeedbackController;
import com.smartparking.notify.handler.GlobalExceptionHandler;
import com.smartparking.notify.mapper.FeedbackMapper;
import com.smartparking.notify.po.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反馈提交归属校验。
 *
 * <p>原实现直接信任 body 里的 {@code userId} —— 业主可替他人提交投诉，
 * 也可在「我的反馈」里传别人的 userId 读到全部内容（IDOR）。现约定：
 * 网关注入的 {@code X-User-Id} 优先，业主视角下 body/参数一律被覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class FeedbackSubmitOwnershipTest {

    @Mock
    private FeedbackMapper feedbackMapper;

    @InjectMocks
    private FeedbackController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("业主提交：body 里的 userId 被网关注入身份覆盖")
    void submit_owner_ignoresBodyUserId() throws Exception {
        when(feedbackMapper.insert(any(Feedback.class))).thenReturn(1);

        mockMvc.perform(post("/api/notify/feedback")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9999,\"type\":1,\"title\":\"道闸故障\",\"content\":\"A 区入口道闸不抬杆\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1001L);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("业主提交：内容为空拒绝落库（4001）")
    void submit_blankContent_rejected() throws Exception {
        mockMvc.perform(post("/api/notify/feedback")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1,\"title\":\"空\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("反馈内容不能为空"));

        verify(feedbackMapper, never()).insert(any(Feedback.class));
    }

    @Test
    @DisplayName("标题缺省时取内容前 20 字，列表不会出现空标题")
    void submit_missingTitle_derivedFromContent() throws Exception {
        when(feedbackMapper.insert(any(Feedback.class))).thenReturn(1);

        String content = "地下车库 B2 层照明长期损坏，夜间取车很不安全，希望尽快维修";
        mockMvc.perform(post("/api/notify/feedback")
                        .header("X-User-Id", "1002")
                        .header("X-User-Role", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":2,\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(content.substring(0, 20));
    }
}

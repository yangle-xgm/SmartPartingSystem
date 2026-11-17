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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反馈回复接口回归测试（前端功能测试 DEF-01 同类问题收口）。
 *
 * <p>原实现：反馈不存在时直接 {@code return Result.success(null)}，前端拿到 200
 * 以为回复成功，实际什么都没写；{@code updateById} 返回 0 行同样当成功。
 * 现约定：记录不存在 / 并发删除 → 5702；回复内容为空 → 4001。</p>
 */
@ExtendWith(MockitoExtension.class)
class FeedbackReplyContractTest {

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
    @DisplayName("POST /{id}/reply：反馈不存在返回 5702，且不执行更新")
    void reply_missingFeedback_returnsFeedbackNotFound() throws Exception {
        when(feedbackMapper.selectById(9001L)).thenReturn(null);

        mockMvc.perform(post("/api/notify/feedback/9001/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"已处理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5702))
                .andExpect(jsonPath("$.message").value("反馈不存在"));

        verify(feedbackMapper, never()).updateById(any(Feedback.class));
    }

    @Test
    @DisplayName("POST /{id}/reply：回复内容为空返回 4001")
    void reply_blankContent_returnsDataInvalid() throws Exception {
        mockMvc.perform(post("/api/notify/feedback/9001/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("回复内容不能为空"));

        verify(feedbackMapper, never()).updateById(any(Feedback.class));
    }

    @Test
    @DisplayName("POST /{id}/reply：正常回复落库 status=2 + replyTime，返回 code=200")
    void reply_success_persistsReply() throws Exception {
        Feedback fb = new Feedback();
        fb.setId(9002L);
        fb.setStatus(0);
        when(feedbackMapper.selectById(9002L)).thenReturn(fb);
        when(feedbackMapper.updateById(any(Feedback.class))).thenReturn(1);

        mockMvc.perform(post("/api/notify/feedback/9002/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"已安排处理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).updateById(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getReply()).isEqualTo("已安排处理");
        assertThat(saved.getStatus()).isEqualTo(2);
        assertThat(saved.getReplyTime()).isInstanceOf(LocalDateTime.class);
    }
}

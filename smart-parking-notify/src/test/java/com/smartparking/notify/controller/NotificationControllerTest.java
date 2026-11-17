package com.smartparking.notify.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 未读数接口单元测试（E2E 全链路验证 2026-11-17 缺陷的防回归闸门）。
 *
 * <p>历史问题：{@code unread/count} 的 {@code userId} 是必填 {@code @RequestParam}，
 * 而 H5 业主端只带 {@code X-User-Id} 请求头调用，接口直接 400 —— 首页红点因此一直是 0。
 * 现在的约定：
 * <ul>
 *   <li>业主（role=0）忽略入参 userId，强制只统计本人；</li>
 *   <li>缺省 userId 时回退取 {@code X-User-Id}；</li>
 *   <li>两者都没有才报参数错误。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Long HEADER_USER = 1001L;
    private static final long UNREAD = 3L;

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
        lenient().when(notificationService.getUnreadCount(anyLong())).thenReturn(UNREAD);
    }

    @Test
    @DisplayName("userId 缺省时回退取 X-User-Id")
    void unreadCount_fallsBackToHeaderUserId() {
        Result<Long> result = controller.unreadCount(null, String.valueOf(HEADER_USER), "0");

        assertEquals(UNREAD, result.getData());
        verify(notificationService).getUnreadCount(HEADER_USER);
    }

    @Test
    @DisplayName("未携带角色头时同样回退取 X-User-Id")
    void unreadCount_withoutRoleHeader_fallsBackToHeaderUserId() {
        controller.unreadCount(null, String.valueOf(HEADER_USER), null);

        verify(notificationService).getUnreadCount(HEADER_USER);
    }

    @Test
    @DisplayName("业主忽略入参 userId，强制统计本人")
    void unreadCount_ownerIgnoresParamUserId() {
        controller.unreadCount(2002L, String.valueOf(HEADER_USER), "0");

        verify(notificationService).getUnreadCount(HEADER_USER);
        verify(notificationService, never()).getUnreadCount(2002L);
    }

    @Test
    @DisplayName("管理员按入参 userId 统计")
    void unreadCount_adminKeepsParamUserId() {
        controller.unreadCount(2002L, String.valueOf(HEADER_USER), "1");

        verify(notificationService).getUnreadCount(2002L);
    }

    @Test
    @DisplayName("userId 与 X-User-Id 都缺失时报参数错误")
    void unreadCount_withoutAnyUserId_throwsParamInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> controller.unreadCount(null, null, "0"));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        verify(notificationService, never()).getUnreadCount(anyLong());
    }
}

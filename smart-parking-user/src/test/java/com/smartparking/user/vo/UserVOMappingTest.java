package com.smartparking.user.vo;

import com.smartparking.user.po.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UserVO} 实体转换测试。
 *
 * <p>回归闸门：{@code /user/info} 曾漏下发 {@code communityId}，H5「我的」只能拿小区档案
 * 列表首条兜底，社区 2/3 的业主被显示成 1 号小区。这里锁住"实体有值 → VO 有值"，
 * 以及"实体为空 → VO 保持 null（前端显示未绑定，而不是错显首条）"。</p>
 */
class UserVOMappingTest {

    @Test
    @DisplayName("from()：communityId 原样带出（非默认小区的业主不能被抹成 1）")
    void from_carriesCommunityId() {
        SysUser user = new SysUser();
        user.setId(1004L);
        user.setUsername("zhaoliu");
        user.setRealName("赵六");
        user.setRole(0);
        user.setStatus(1);
        user.setCommunityId(2L);
        user.setCreateTime(LocalDateTime.of(2026, 1, 8, 9, 30));

        UserVO vo = UserVO.from(user);

        assertEquals(1004L, vo.getId());
        assertEquals("zhaoliu", vo.getUsername());
        assertEquals("赵六", vo.getRealName());
        assertEquals(2L, vo.getCommunityId());
        assertEquals(user.getCreateTime(), vo.getCreateTime());
    }

    @Test
    @DisplayName("from()：实体 communityId 为 null 时 VO 也为 null（前端据此显示未绑定小区）")
    void from_nullCommunityIdStaysNull() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("legacy");

        UserVO vo = UserVO.from(user);

        assertNull(vo.getCommunityId());
    }
}

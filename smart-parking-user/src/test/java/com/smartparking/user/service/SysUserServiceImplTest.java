package com.smartparking.user.service;

import com.smartparking.user.dto.RegisterDTO;
import com.smartparking.user.mapper.SysUserMapper;
import com.smartparking.user.monitor.UserAuthMetrics;
import com.smartparking.user.po.SysUser;
import com.smartparking.user.service.impl.SysUserServiceImpl;
import com.smartparking.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试（不连 Spring 上下文 / 不连数据库）。
 *
 * <p>本次守护的缺陷：{@code register()} 此前不写 communityId，靠 {@code sys_user.community_id}
 * 的 DB DEFAULT 1 兜底——库里是 1，返回给前端的 VO 却是 null，于是注册后立刻渲染的
 * 「我的」页拿不到小区归属。现改为显式写入默认小区，两边口径一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private static final Long DEFAULT_COMMUNITY_ID = 1L;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserAuthMetrics userAuthMetrics;

    @Mock
    private SysUserMapper sysUserMapper;

    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new SysUserServiceImpl(auditLogService, redisTemplate, userAuthMetrics);
        // ServiceImpl 的 baseMapper 由 MyBatis-Plus 注入，单测里手工塞入
        ReflectionTestUtils.setField(userService, "baseMapper", sysUserMapper);
    }

    @Test
    @DisplayName("注册：显式写入默认小区 communityId=1，响应 VO 也带得出该值")
    void register_writesDefaultCommunityId_andReturnsIt() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            // 模拟 MyBatis-Plus 回填主键
            SysUser inserting = invocation.getArgument(0);
            inserting.setId(9001L);
            return 1;
        });

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newowner");
        dto.setPassword("pwd123456");
        dto.setRealName("新业主");

        UserVO vo = userService.register(dto);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        org.mockito.Mockito.verify(sysUserMapper).insert(captor.capture());
        SysUser inserted = captor.getValue();

        // 落库值与 DB DEFAULT 1 一致，不再是 null 依赖数据库默认值
        assertEquals(DEFAULT_COMMUNITY_ID, inserted.getCommunityId());
        assertEquals(0, inserted.getRole());
        assertEquals(1, inserted.getStatus());
        // 注册响应可被前端直接用于展示小区归属
        assertEquals(9001L, vo.getId());
        assertEquals(DEFAULT_COMMUNITY_ID, vo.getCommunityId());
    }
}

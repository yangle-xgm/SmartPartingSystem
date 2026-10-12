package com.smartparking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.common.core.utils.JwtUtils;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.user.dto.RegisterDTO;
import com.smartparking.user.dto.UserUpdateDTO;
import com.smartparking.user.mapper.SysUserMapper;
import com.smartparking.user.po.SysUser;
import com.smartparking.user.query.SysUserQuery;
import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import com.smartparking.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final AuditLogService auditLogService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Override
    public UserVO register(RegisterDTO dto) {
        // 检查用户名是否已存在
        Long count = this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        // 密码加密（BCrypt）
        user.setPassword(passwordEncode(dto.getPassword()));
        // 手机号加密存储
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(AesUtils.encrypt(dto.getPhone()));
        }
        user.setRealName(dto.getRealName());
        // 默认角色为业主
        user.setRole(0);
        user.setStatus(1);

        this.save(user);
        auditLogService.record(user.getId(), user.getUsername(), "注册", "POST /api/user/register",
                "{\"username\":\"" + user.getUsername() + "\"}", true, null, null);
        return UserVO.from(user);
    }

    @Override
    public Map<String, String> login(String username, String password, String captcha, String captchaKey) {
        try {
            // 校验图形验证码
            if (captchaKey == null || captcha == null) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "请输入验证码");
            }
            String expected = redisTemplate.opsForValue().get("captcha:" + captchaKey);
            if (expected == null || !expected.equalsIgnoreCase(captcha.trim())) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "验证码错误或已过期");
            }
            redisTemplate.delete("captcha:" + captchaKey);

            SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username));

            if (user == null) {
                throw new BizException(ErrorCode.LOGIN_FAILED);
            }
            if (user.getStatus() == 0) {
                throw new BizException(ErrorCode.USER_DISABLED);
            }
            if (!passwordMatches(password, user.getPassword())) {
                throw new BizException(ErrorCode.LOGIN_FAILED);
            }

            auditLogService.record(user.getId(), user.getUsername(), "登录", "POST /api/user/login",
                    "{\"username\":\"" + username + "\"}", true, null, null);
            Map<String, String> tokens = new HashMap<>();
            Long communityId = user.getCommunityId() != null ? user.getCommunityId() : 1L;
            tokens.put("accessToken", JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            tokens.put("refreshToken", JwtUtils.generateRefreshToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            return tokens;
        } catch (RuntimeException e) {
            auditLogService.record(null, username, "登录", "POST /api/user/login",
                    "{\"username\":\"" + username + "\",\"reason\":\"" + e.getMessage() + "\"}",
                    false, null, null);
            throw e;
        }
    }

    @Override
    public Map<String, String> refreshToken(String refreshToken) {
        try {
            Long userId = JwtUtils.getUserId(refreshToken);
            String username = JwtUtils.getUsername(refreshToken);
            Integer role = JwtUtils.getRole(refreshToken);
            SysUser user = this.getById(userId);
            if (user == null || user.getStatus() == 0) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "用户不存在或已禁用");
            }
            Map<String, String> tokens = new HashMap<>();
            Long communityId = user.getCommunityId() != null ? user.getCommunityId() : 1L;
            tokens.put("accessToken", JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            tokens.put("refreshToken", JwtUtils.generateRefreshToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            return tokens;
        } catch (Exception e) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "refreshToken无效");
        }
    }

    @Override
    public Map<String, String> generateCaptcha() {
        int a = (int) (Math.random() * 8 + 1);
        int b = (int) (Math.random() * 8 + 1);
        String answer = String.valueOf(a + b);
        String key = java.util.UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("captcha:" + key, answer, java.time.Duration.ofMinutes(5));
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='40'>"
                + "<rect width='120' height='40' fill='#f0f9eb'/>"
                + "<text x='15' y='28' font-family='Arial' font-size='22' fill='#409eff'>" + a + " + " + b + " = ?</text>"
                + "</svg>";
        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", key);
        result.put("captchaImg", "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(svg.getBytes()));
        return result;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toVoWithPhoneMasked(user);
    }

    @Override
    public void updateUser(UserUpdateDTO dto) {
        SysUser existUser = this.getById(dto.getId());
        if (existUser == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        SysUser user = new SysUser();
        user.setId(dto.getId());
        // 手机号加密存储
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(AesUtils.encrypt(dto.getPhone()));
        }
        user.setRealName(dto.getRealName());
        user.setAvatar(dto.getAvatar());

        this.updateById(user);

        auditLogService.record(dto.getId(), existUser.getUsername(), "修改用户", "PUT /api/user/update",
                "{\"id\":" + dto.getId() + ",\"realName\":\"" + dto.getRealName() + "\"}", true, null, null);
    }

    @Override
    public Page<UserVO> pageList(Page<SysUser> page, SysUserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .eq(query.getRole() != null, SysUser::getRole, query.getRole())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> userPage = this.page(page, wrapper);

        List<UserVO> records = userPage.getRecords().stream()
                .map(this::toVoWithPhoneMasked)
                .toList();
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * 转为VO并脱敏手机号
     */
    private UserVO toVoWithPhoneMasked(SysUser user) {
        UserVO vo = UserVO.from(user);
        if (StringUtils.hasText(user.getPhone())) {
            vo.setPhoneMasked(AesUtils.maskPhone(AesUtils.decrypt(user.getPhone())));
        }
        return vo;
    }

    /**
     * 查询全部业主用户ID（角色=0，供通知广播等内部使用）
     */
    @Override
    public List<Long> listOwnerIds() {
        return this.list(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, 0)
                        .eq(SysUser::getStatus, 1)
                        .select(SysUser::getId))
                .stream().map(SysUser::getId).toList();
    }

    /**
     * 密码加密（模拟BCrypt，生产环境使用BCryptPasswordEncoder）
     */
    private String passwordEncode(String rawPassword) {
        return "$2a$10$" + DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 密码校验
     */
    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        return passwordEncode(rawPassword).equals(encodedPassword);
    }
}

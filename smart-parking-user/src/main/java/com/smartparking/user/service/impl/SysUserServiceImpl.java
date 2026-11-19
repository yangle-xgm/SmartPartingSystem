package com.smartparking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.common.core.utils.JwtUtils;
import com.smartparking.common.core.web.WebRequestUtils;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.user.dto.RegisterDTO;
import com.smartparking.user.dto.UserUpdateDTO;
import com.smartparking.user.mapper.SysUserMapper;
import com.smartparking.user.monitor.UserAuthMetrics;
import com.smartparking.user.po.SysUser;
import com.smartparking.user.query.SysUserQuery;
import com.smartparking.user.security.PasswordCrypto;
import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import com.smartparking.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final AuditLogService auditLogService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final UserAuthMetrics userAuthMetrics;

    /**
     * 默认小区ID：与 {@code sys_user.community_id} 的 DB DEFAULT 1 保持一致。
     * 注册时显式写入，保证注册响应里的 VO 也带得出 communityId（否则 DB 有值、VO 为 null）。
     */
    private static final Long DEFAULT_COMMUNITY_ID = 1L;

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
        // 密码加密（真 BCrypt，含随机盐）
        user.setPassword(PasswordCrypto.encode(dto.getPassword()));
        // 手机号加密存储
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(AesUtils.encrypt(dto.getPhone()));
        }
        user.setRealName(dto.getRealName());
        // 默认角色为业主
        user.setRole(0);
        user.setStatus(1);
        // 归属默认小区（与 DB DEFAULT 1 一致），保证注册响应可回填 communityId
        user.setCommunityId(DEFAULT_COMMUNITY_ID);

        this.save(user);
        userAuthMetrics.onRegister();
        auditLogService.record(user.getId(), user.getUsername(), "注册", "POST /api/user/register",
                "{\"username\":\"" + user.getUsername() + "\"}", true, WebRequestUtils.currentIp(), WebRequestUtils.elapsedMillis());
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
            if (!PasswordCrypto.matches(password, user.getPassword())) {
                throw new BizException(ErrorCode.LOGIN_FAILED);
            }

            // 存量「伪 BCrypt」（$2a$10$+MD5，无盐）校验通过后立即升级为真 BCrypt。
            // 用户无感知，且升级失败不影响本次登录（下次登录会再试）。
            if (PasswordCrypto.isLegacy(user.getPassword())) {
                try {
                    SysUser upgrade = new SysUser();
                    upgrade.setId(user.getId());
                    upgrade.setPassword(PasswordCrypto.encode(password));
                    this.updateById(upgrade);
                    log.info("用户 {} 的密码哈希已升级为 BCrypt", user.getUsername());
                } catch (Exception e) {
                    log.warn("密码哈希升级失败 username={}, err={}", user.getUsername(), e.getMessage());
                }
            }

            auditLogService.record(user.getId(), user.getUsername(), "登录", "POST /api/user/login",
                    "{\"username\":\"" + username + "\"}", true, WebRequestUtils.currentIp(), WebRequestUtils.elapsedMillis());
            Map<String, String> tokens = new HashMap<>();
            userAuthMetrics.onLoginSuccess();
            Long communityId = user.getCommunityId() != null ? user.getCommunityId() : DEFAULT_COMMUNITY_ID;
            tokens.put("accessToken", JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            tokens.put("refreshToken", JwtUtils.generateRefreshToken(user.getId(), user.getUsername(), user.getRole(), communityId));
            return tokens;
        } catch (RuntimeException e) {
            userAuthMetrics.onLoginFail();
            auditLogService.record(null, username, "登录", "POST /api/user/login",
                    "{\"username\":\"" + username + "\",\"reason\":\"" + e.getMessage() + "\"}",
                    false, WebRequestUtils.currentIp(), WebRequestUtils.elapsedMillis());
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
            Long communityId = user.getCommunityId() != null ? user.getCommunityId() : DEFAULT_COMMUNITY_ID;
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
                "{\"id\":" + dto.getId() + ",\"realName\":\"" + dto.getRealName() + "\"}", true, WebRequestUtils.currentIp(), WebRequestUtils.elapsedMillis());
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
}

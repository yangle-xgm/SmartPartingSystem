package com.smartparking.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.user.dto.RegisterDTO;
import com.smartparking.user.dto.UserUpdateDTO;
import com.smartparking.user.po.SysUser;
import com.smartparking.user.query.SysUserQuery;
import com.smartparking.user.vo.UserVO;

import java.util.Map;

/**
 * 用户服务接口
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户注册
     */
    UserVO register(RegisterDTO dto);

    /**
     * 用户登录，返回 accessToken + refreshToken
     */
    Map<String, String> login(String username, String password, String captcha, String captchaKey);

    /**
     * 生成图形验证码
     */
    Map<String, String> generateCaptcha();

    /**
     * 用refreshToken刷新accessToken
     */
    Map<String, String> refreshToken(String refreshToken);

    /**
     * 获取用户信息
     */
    UserVO getUserInfo(Long userId);

    /**
     * 更新用户信息
     */
    void updateUser(UserUpdateDTO dto);

    /**
     * 分页查询用户列表
     */
    Page<UserVO> pageList(Page<SysUser> page, SysUserQuery query);

    /**
     * 查询全部业主用户ID（角色=0，供通知广播等内部使用）
     */
    java.util.List<Long> listOwnerIds();
}

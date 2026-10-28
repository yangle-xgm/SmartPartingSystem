package com.smartparking.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.user.dto.LoginDTO;
import com.smartparking.user.dto.RegisterDTO;
import com.smartparking.user.dto.UserUpdateDTO;
import com.smartparking.user.query.SysUserQuery;
import com.smartparking.user.service.AuditLogService;
import com.smartparking.user.service.SysUserService;
import com.smartparking.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 用户控制器
 */
@Tag(name = "用户管理", description = "用户注册、登录、信息管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final AuditLogService auditLogService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody RegisterDTO dto) {
        return Result.success(sysUserService.register(dto));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<java.util.Map<String, String>> login(@RequestBody LoginDTO dto) {
        return Result.success(sysUserService.login(dto.getUsername(), dto.getPassword(), dto.getCaptcha(), dto.getCaptchaKey()));
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<java.util.Map<String, String>> captcha() {
        return Result.success(sysUserService.generateCaptcha());
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<java.util.Map<String, String>> refresh(@RequestHeader("Authorization") String auth) {
        String refreshToken = auth.replace("Bearer ", "");
        return Result.success(sysUserService.refreshToken(refreshToken));
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserVO> getUserInfo(
            @Parameter(description = "用户ID（业主可省略，取当前登录人）") @RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主只能查自己：忽略前端传入的 userId，杜绝越权查看他人资料
        Long targetId = userId;
        if ("0".equals(currentRole) && currentUserId != null) {
            targetId = Long.valueOf(currentUserId);
        }
        if (targetId == null) {
            return Result.fail(ErrorCode.PARAM_INVALID, "缺少 userId 参数");
        }
        return Result.success(sysUserService.getUserInfo(targetId));
    }

    @Operation(summary = "分页查询用户列表")
    @GetMapping("/list")
    public Result<PageResult<UserVO>> list(SysUserQuery query) {
        Page<UserVO> page = sysUserService.pageList(
                new Page<>(query.getCurrent(), query.getSize()), query);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody UserUpdateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主只能修改自己的资料（管理端 role=1/2 不受限制）
        if ("0".equals(currentRole) && currentUserId != null) {
            dto.setId(Long.valueOf(currentUserId));
        }
        sysUserService.updateUser(dto);
        return Result.success();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/upload-avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        String dir = System.getProperty("user.dir") + "/uploads/avatars/";
        new File(dir).mkdirs();
        String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
        String filename = UUID.randomUUID() + ext;
        file.transferTo(new File(dir + filename));
        return Result.success("/uploads/avatars/" + filename);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@Parameter(description = "用户ID") @RequestParam Long userId) {
        // 记录退出日志（用户服务内直接审计）
        auditLogService.record(userId, null, "退出登录", "POST /api/user/logout",
                "{\"userId\":" + userId + "}", true, null, null);
        return Result.success();
    }

    @Operation(summary = "查询全部业主ID（内部接口，通知广播使用）")
    @GetMapping("/internal/owner-ids")
    public Result<java.util.List<Long>> ownerIds() {
        return Result.success(sysUserService.listOwnerIds());
    }
}

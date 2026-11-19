package com.smartparking.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.web.WebRequestUtils;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.exception.BizException;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** 允许的头像扩展名 */
    private static final Set<String> ALLOWED_AVATAR_EXT = Set.of("png", "jpg", "jpeg", "webp");
    /** 头像文件名白名单：UUID/短横线 + 图片后缀，天然不含路径分隔符 */
    private static final Pattern AVATAR_FILE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}\\.(png|jpg|jpeg|webp)$");

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@jakarta.validation.Valid @RequestBody RegisterDTO dto) {
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
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        // 只接受图片扩展名：此前不校验，可上传 .jsp/.html 等任意文件到可公开访问目录
        if (!ALLOWED_AVATAR_EXT.contains(ext)) {
            throw new BizException(ErrorCode.DATA_INVALID, "头像仅支持 JPG / PNG / WEBP 图片");
        }
        File dir = avatarDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException(ErrorCode.DATA_INVALID, "头像目录创建失败");
        }
        String filename = UUID.randomUUID() + "." + ext;
        file.transferTo(new File(dir, filename));
        // 返回网关可路由、且 <img> 能直接引用（免 Authorization 头）的地址
        return Result.success("/api/user/avatar/" + filename);
    }

    /**
     * 头像文件读取。
     *
     * <p>此前上传后返回 {@code /uploads/avatars/xxx.png}，但网关没有该前缀的路由、
     * 服务也没有静态资源映射 —— 业主与管理端上传成功却全是裂图。现由本接口按文件名
     * 回读，路径经白名单正则校验（只允许 UUID 形态 + 图片后缀），不含分隔符，
     * 从根上排除目录穿越。该路径在网关 WHITE_LIST 内（浏览器 img 标签不携带 JWT）。</p>
     */
    @Operation(summary = "头像文件（公开读取，供 img 标签直接引用）")
    @GetMapping("/avatar/{filename}")
    public ResponseEntity<byte[]> avatar(@PathVariable String filename) throws IOException {
        if (!AVATAR_FILE_PATTERN.matcher(filename).matches()) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(avatarDir(), filename);
        if (!file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        MediaType type = "png".equals(ext) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(Files.readAllBytes(file.toPath()));
    }

    /** 头像目录：与写入保持一致，均以进程工作目录为基准 */
    private File avatarDir() {
        return new File(System.getProperty("user.dir") + "/uploads/avatars/");
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@Parameter(description = "用户ID") @RequestParam Long userId) {
        // 记录退出日志（用户服务内直接审计）
        auditLogService.record(userId, null, "退出登录", "POST /api/user/logout",
                "{\"userId\":" + userId + "}", true, WebRequestUtils.currentIp(), WebRequestUtils.elapsedMillis());
        return Result.success();
    }

    @Operation(summary = "查询全部业主ID（内部接口，通知广播使用）")
    @GetMapping("/internal/owner-ids")
    public Result<java.util.List<Long>> ownerIds() {
        return Result.success(sysUserService.listOwnerIds());
    }
}

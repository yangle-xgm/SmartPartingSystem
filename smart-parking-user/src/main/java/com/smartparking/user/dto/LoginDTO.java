package com.smartparking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户登录DTO
 */
@Data
@Schema(description = "用户登录")
public class LoginDTO {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "验证码答案")
    private String captcha;

    @Schema(description = "验证码key")
    private String captchaKey;
}

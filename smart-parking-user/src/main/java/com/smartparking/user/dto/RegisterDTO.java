package com.smartparking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户注册DTO
 */
@Data
@Schema(description = "用户注册")
public class RegisterDTO {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "真实姓名")
    private String realName;
}

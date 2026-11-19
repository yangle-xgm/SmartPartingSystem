package com.smartparking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册DTO
 */
@Data
@Schema(description = "用户注册")
public class RegisterDTO {

    // 报告 P2-14：注册入参此前零校验（username/password 可为 null 或空串，直接落库）。
    // 规则与前端 register/index.vue 对齐：用户名 3-32、密码 6-20。
    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "用户名只能包含字母、数字、下划线和短横线")
    private String username;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度 6-20 位")
    @Pattern(regexp = "^\\S+$", message = "密码不能包含空白字符")
    private String password;

    @Schema(description = "手机号")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "真实姓名")
    @Size(max = 32, message = "姓名长度不能超过 32 位")
    private String realName;
}

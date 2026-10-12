package com.smartparking.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新用户信息DTO
 */
@Data
@Schema(description = "更新用户信息")
public class UserUpdateDTO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像地址")
    private String avatar;
}

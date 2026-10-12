package com.smartparking.user.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@Schema(description = "用户")
public class SysUser extends BaseEntity {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "手机号（加密存储）")
    private String phone;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色（0业主 1管理员 2超级管理员）")
    private Integer role;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "状态（0禁用 1正常）")
    private Integer status;

    @Schema(description = "所属小区ID（默认1）")
    private Long communityId;

    /** 手机号脱敏显示（非数据库字段） */
    @TableField(exist = false)
    private String phoneMasked;
}

package com.smartparking.user.vo;

import com.smartparking.common.core.annotation.Sensitive;
import com.smartparking.user.po.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户展示对象
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号（脱敏显示）")
    private String phoneMasked;

    @Sensitive(type = "name")
    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色（0业主 1管理员 2超级管理员）")
    private Integer role;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "状态（0禁用 1正常）")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 由实体转换（不含脱敏手机号，由服务层填充）
     */
    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

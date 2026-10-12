package com.smartparking.user.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询条件")
public class SysUserQuery extends PageQuery {

    @Schema(description = "用户名（模糊匹配）")
    private String username;

    @Schema(description = "角色（0业主 1管理员 2超级管理员）")
    private Integer role;

    @Schema(description = "状态（0禁用 1正常）")
    private Integer status;
}

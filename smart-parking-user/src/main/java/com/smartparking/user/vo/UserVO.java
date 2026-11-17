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

    /**
     * 真实姓名。
     *
     * <p>E2E 报告 P2-2：此前此处挂 {@code @Sensitive(type="name")}，{@code /user/info} 返回"张*"，
     * 而 profile/user 页面把该值回填输入框，保存即把真实姓名覆盖为掩码。
     * 本 VO 的消费方均为有权限场景（{@code /user/info} 本人视角、{@code /user/list} 仅管理员），
     * 故不脱敏；手机号另有 phoneMasked 显式掩码字段承接脱敏诉求。</p>
     */
    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色（0业主 1管理员 2超级管理员）")
    private Integer role;

    /**
     * 所属小区ID。
     *
     * <p>E2E 报告遗留缺口：此前 VO 不含该字段，H5「我的」拿不到 {@code communityId}，
     * 只能回退到小区档案列表首条来显示小区名——多小区数据下（社区 2/3 的业主）
     * 会显示成 1 号小区，属于"看着对、其实错"的静默缺陷。
     * 现按 {@code sys_user.community_id} 原样下发，由前端按 id 精确匹配。</p>
     */
    @Schema(description = "所属小区ID")
    private Long communityId;

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
        vo.setCommunityId(user.getCommunityId());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

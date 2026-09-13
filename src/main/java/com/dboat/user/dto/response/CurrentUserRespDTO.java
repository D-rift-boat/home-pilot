package com.dboat.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息响应 DTO
 * <p>
 * 前端刷新页面后调用 {@code /api/auth/currentUser} 拉取用户资料、角色与权限集合，
 * 用于菜单渲染与按钮级权限控制。时间字段统一使用毫秒时间戳，避免 LocalDateTime 序列化为数组。
 * </p>
 *
 * @author dboat
 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class CurrentUserRespDTO implements Serializable {

    /** 用户ID */
    @Schema(description = "用户ID")
    private String userId;

    /** 租户ID */
    @Schema(description = "租户ID")
    private String orgId;

    /** 租户名称 */
    @Schema(description = "租户名称")
    private String orgName;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 头像地址 */
    @Schema(description = "头像地址")
    private String avatarUrl;

    /** 账号状态：0禁用 1正常 2锁定 */
    @Schema(description = "账号状态：0禁用 1正常 2锁定", example = "1")
    private Integer status;

    /** 是否首次登录（true 需强制修改密码） */
    @Schema(description = "是否首次登录")
    private Boolean firstLogin;

    /** 角色编码集合，如 ["ADMIN"] */
    @Schema(description = "角色编码集合")
    private List<String> roleKeys;

    /** 角色名称集合，如 ["租户管理员"] */
    @Schema(description = "角色名称集合")
    private List<String> roleNames;

    /** 权限编码集合，如 ["iot:device:read"] */
    @Schema(description = "权限编码集合")
    private List<String> perms;

    /** 最后登录时间（毫秒时间戳） */
    @Schema(description = "最后登录时间（毫秒时间戳）")
    private Long lastLoginTime;

    /** 最后登录IP */
    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    /** 账号创建时间（毫秒时间戳） */
    @Schema(description = "账号创建时间（毫秒时间戳）")
    private Long createTime;
}

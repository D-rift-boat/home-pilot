package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.UserAuth;

import java.util.List;

/**
* @author tanghj
* @description 针对表【user_auth(用户认证凭据表（登录凭据与用户业务解耦）)】的数据库操作Service
* @createDate 2026-09-13 17:53:17
*/
public interface UserAuthService extends IService<UserAuth> {

    /**
     * 查询用户的密码认证凭据
     *
     * @param userId 用户ID
     * @return 密码认证记录，不存在时返回 null
     */
    UserAuth getPasswordAuthByUserId(String userId);

    /**
     * 按登录标识查询密码认证凭据
     * <p>identifier 可为登录账号 / 手机号 / 邮箱，orgId 为空时跨租户查询。</p>
     *
     * @param orgId      租户ID，可为空
     * @param identifier 登录标识
     * @return 密码认证记录列表（跨租户时可能命中多条）
     */
    List<UserAuth> listPasswordAuthByIdentifier(String orgId, String identifier);

    /**
     * 判断登录标识在指定认证方式下是否已被占用
     *
     * @param orgId      租户ID
     * @param identifier 登录标识
     * @param authType   认证方式，见 {@link com.dboat.user.enums.AuthTypeEnum}
     * @return true=已存在
     */
    boolean existsIdentifier(String orgId, String identifier, Integer authType);

    /**
     * 更新密码凭据：写入新 BCrypt 哈希，并将 password_version +1、刷新最后改密时间
     * <p>password_version 变更后，该用户旧令牌对应的权限缓存立即失效。</p>
     *
     * @param userId         用户ID
     * @param encodedPassword BCrypt 哈希后的新密码
     * @return true=更新成功
     */
    boolean updatePassword(String userId, String encodedPassword);
}

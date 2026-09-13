package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.UserAuth;
import com.dboat.user.enums.AuthTypeEnum;
import com.dboat.user.mapper.UserAuthMapper;
import com.dboat.user.service.UserAuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
* @author tanghj
* @description 针对表【user_auth(用户认证凭据表（登录凭据与用户业务解耦）)】的数据库操作Service实现
* @createDate 2026-09-13 17:53:17
*/
@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthMapper, UserAuth>
    implements UserAuthService{

    /**
     * 查询用户的密码认证凭据
     *
     * @param userId 用户ID
     * @return 密码认证记录，不存在时返回 null
     */
    @Override
    public UserAuth getPasswordAuthByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return getOne(Wrappers.<UserAuth>lambdaQuery()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getAuthType, AuthTypeEnum.PASSWORD.getCode())
                .isNull(UserAuth::getDeleteTime)
                .last("LIMIT 1"));
    }

    /**
     * 按登录标识查询密码认证凭据
     *
     * @param orgId      租户ID，可为空（为空时跨租户查询）
     * @param identifier 登录标识
     * @return 密码认证记录列表
     */
    @Override
    public List<UserAuth> listPasswordAuthByIdentifier(String orgId, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<UserAuth>lambdaQuery()
                .eq(StringUtils.hasText(orgId), UserAuth::getOrgId, orgId)
                .eq(UserAuth::getAuthType, AuthTypeEnum.PASSWORD.getCode())
                .eq(UserAuth::getIdentifier, identifier)
                .isNull(UserAuth::getDeleteTime));
    }

    /**
     * 判断登录标识在指定认证方式下是否已被占用
     *
     * @param orgId      租户ID
     * @param identifier 登录标识
     * @param authType   认证方式
     * @return true=已存在
     */
    @Override
    public boolean existsIdentifier(String orgId, String identifier, Integer authType) {
        return exists(Wrappers.<UserAuth>lambdaQuery()
                .eq(UserAuth::getOrgId, orgId)
                .eq(UserAuth::getIdentifier, identifier)
                .eq(UserAuth::getAuthType, authType)
                .isNull(UserAuth::getDeleteTime));
    }

    /**
     * 更新密码凭据：写入新 BCrypt 哈希，password_version +1，刷新最后改密时间
     *
     * @param userId          用户ID
     * @param encodedPassword BCrypt 哈希后的新密码
     * @return true=更新成功
     */
    @Override
    public boolean updatePassword(String userId, String encodedPassword) {
        return update(Wrappers.<UserAuth>lambdaUpdate()
                .set(UserAuth::getCredential, encodedPassword)
                .set(UserAuth::getLastPasswordChange, LocalDateTime.now())
                // 数据库侧原子自增，避免并发覆盖
                .setSql("password_version = password_version + 1")
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getAuthType, AuthTypeEnum.PASSWORD.getCode())
                .isNull(UserAuth::getDeleteTime));
    }
}

package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.User;
import com.dboat.user.enums.UserStatusEnum;
import com.dboat.user.mapper.UserMapper;
import com.dboat.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
* @author tanghj
* @description 针对表【user(系统用户主表（RBAC主体）)】的数据库操作Service实现
* @createDate 2026-09-13 17:49:51
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    /**
     * 按用户ID查询未删除用户
     *
     * @param userId 用户ID
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public User getUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime)
                .last("LIMIT 1"));
    }

    /**
     * 按租户 + 登录账号查询未删除用户
     *
     * @param orgId    租户ID，为空时不限制租户
     * @param username 登录账号
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public User getByOrgIdAndUsername(String orgId, String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return getOne(Wrappers.<User>lambdaQuery()
                // orgId 为空时不追加租户条件，用于跨租户按账号全局匹配
                .eq(StringUtils.hasText(orgId), User::getOrgId, orgId)
                .eq(User::getUsername, username)
                .isNull(User::getDeleteTime)
                .last("LIMIT 1"));
    }

    /**
     * 判断租户内登录账号是否已存在
     *
     * @param orgId    租户ID
     * @param username 登录账号
     * @return true=已存在
     */
    @Override
    public boolean existsUsername(String orgId, String username) {
        return exists(Wrappers.<User>lambdaQuery()
                .eq(User::getOrgId, orgId)
                .eq(User::getUsername, username)
                .isNull(User::getDeleteTime));
    }

    /**
     * 统计平台全部未删除用户数
     *
     * @return 用户总数
     */
    @Override
    public long countPlatformUsers() {
        return count(Wrappers.<User>lambdaQuery().isNull(User::getDeleteTime));
    }

    /**
     * 令牌版本号 +1（数据库侧原子自增，避免并发覆盖）
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    @Override
    public boolean increaseTokenVersion(String userId) {
        return update(Wrappers.<User>lambdaUpdate()
                .setSql("token_version = token_version + 1")
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime));
    }

    /**
     * 记录登录失败：更新连续失败次数，达到阈值时同步置为锁定状态并写入解锁时间
     *
     * @param userId         用户ID
     * @param failCount      累计失败次数
     * @param lockExpireTime 锁定截止时间，未触发锁定时传 null
     * @return true=更新成功
     */
    @Override
    public boolean markLoginFail(String userId, int failCount, LocalDateTime lockExpireTime) {
        boolean locked = lockExpireTime != null;
        return update(Wrappers.<User>lambdaUpdate()
                .set(User::getLoginFailCount, failCount)
                // 仅在触发锁定时更新状态与解锁时间，避免误改被管理员禁用的账号状态
                .set(locked, User::getStatus, UserStatusEnum.LOCKED.getCode())
                .set(locked, User::getLockExpireTime, lockExpireTime)
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime));
    }

    /**
     * 重置登录失败状态：失败次数清零、解除锁定
     * <p>状态字段使用条件 SQL，仅将"锁定"恢复为"正常"，不会误改"禁用"状态。</p>
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    @Override
    public boolean resetLoginFailState(String userId) {
        return update(Wrappers.<User>lambdaUpdate()
                .set(User::getLoginFailCount, 0)
                .set(User::getLockExpireTime, null)
                .setSql("status = IF(status = " + UserStatusEnum.LOCKED.getCode() + ", "
                        + UserStatusEnum.NORMAL.getCode() + ", status)")
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime));
    }

    /**
     * 记录登录成功：刷新最后登录时间与登录IP
     *
     * @param userId  用户ID
     * @param loginIp 登录客户端IP
     * @return true=更新成功
     */
    @Override
    public boolean markLoginSuccess(String userId, String loginIp) {
        return update(Wrappers.<User>lambdaUpdate()
                .set(User::getLastLoginTime, LocalDateTime.now())
                .set(User::getLastLoginIp, loginIp)
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime));
    }

    /**
     * 清除首次登录标记
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    @Override
    public boolean clearFirstLoginFlag(String userId) {
        return update(Wrappers.<User>lambdaUpdate()
                .set(User::getFirstLogin, 0)
                .eq(User::getUserId, userId)
                .isNull(User::getDeleteTime));
    }
}

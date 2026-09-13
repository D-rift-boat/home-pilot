package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.User;

import java.time.LocalDateTime;

/**
* @author tanghj
* @description 针对表【user(系统用户主表（RBAC主体）)】的数据库操作Service
* @createDate 2026-09-13 17:49:51
*/
public interface UserService extends IService<User> {

    /**
     * 按用户ID查询未删除用户
     *
     * @param userId 用户ID
     * @return 用户实体，不存在时返回 null
     */
    User getUserById(String userId);

    /**
     * 按租户 + 登录账号查询未删除用户
     *
     * @param orgId    租户ID，为空时不限制租户
     * @param username 登录账号
     * @return 用户实体，不存在时返回 null
     */
    User getByOrgIdAndUsername(String orgId, String username);

    /**
     * 判断租户内登录账号是否已存在
     *
     * @param orgId    租户ID
     * @param username 登录账号
     * @return true=已存在
     */
    boolean existsUsername(String orgId, String username);

    /**
     * 统计平台全部未删除用户数
     * <p>用于注册流程判定"平台首个用户"，首个用户自动创建租户并授予 ADMIN。</p>
     *
     * @return 用户总数
     */
    long countPlatformUsers();

    /**
     * 令牌版本号 +1，使该用户所有已签发的 Access Token / Refresh Token 立即失效
     * <p>触发场景：修改密码、管理员踢人、封号、检测到 Refresh Token 重放。</p>
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    boolean increaseTokenVersion(String userId);

    /**
     * 记录登录失败：更新连续失败次数，达到阈值时同步置为锁定状态并写入解锁时间
     *
     * @param userId         用户ID
     * @param failCount      累计失败次数
     * @param lockExpireTime 锁定截止时间，未触发锁定时传 null
     * @return true=更新成功
     */
    boolean markLoginFail(String userId, int failCount, LocalDateTime lockExpireTime);

    /**
     * 重置登录失败状态：失败次数清零、解除锁定、状态恢复正常
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    boolean resetLoginFailState(String userId);

    /**
     * 记录登录成功：刷新最后登录时间与登录IP
     *
     * @param userId  用户ID
     * @param loginIp 登录客户端IP
     * @return true=更新成功
     */
    boolean markLoginSuccess(String userId, String loginIp);

    /**
     * 清除首次登录标记（用户完成强制改密后调用）
     *
     * @param userId 用户ID
     * @return true=更新成功
     */
    boolean clearFirstLoginFlag(String userId);
}

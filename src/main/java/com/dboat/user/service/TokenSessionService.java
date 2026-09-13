package com.dboat.user.service;

import com.dboat.user.dto.session.RefreshConsumeResult;
import com.dboat.user.dto.session.UserSessionDTO;
import com.dboat.user.security.LoginUser;

import java.util.List;

/**
 * 登录会话与 Refresh Token 管理服务（Redis 有状态）
 * <p>
 * 承担双令牌机制中「有状态」的一半职责：
 * <ul>
 *   <li>会话创建：写入 Refresh Token 映射、会话详情、用户会话索引</li>
 *   <li>并发会话控制：单用户会话数超限时按登录时间踢掉最早的会话</li>
 *   <li>Refresh Token 轮换：原子消费旧值 + 重放检测，重放时撤销全部会话</li>
 *   <li>会话撤销：单会话登出、全端下线</li>
 *   <li>Access Token 黑名单：登出后在自然过期前主动失效</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
public interface TokenSessionService {

    /**
     * 创建登录会话
     * <p>
     * 依次写入：Refresh Token → 会话映射、会话详情、用户会话索引（ZSet）。
     * 写入索引时执行并发会话裁剪，超限的最早会话被自动清理。
     * </p>
     *
     * @param user         登录用户主体
     * @param sessionId    会话ID（与 Access Token 的 jti 一致）
     * @param refreshToken Refresh Token
     * @param loginIp      登录客户端IP
     * @param userAgent    客户端 UA
     * @param channel      登录渠道
     * @param loginType    登录类型
     * @return 已落库的会话信息
     */
    UserSessionDTO createSession(LoginUser user, String sessionId, String refreshToken,
                                 String loginIp, String userAgent, Integer channel, Integer loginType);

    /**
     * 查询会话详情
     *
     * @param sessionId 会话ID
     * @return 会话信息，不存在时返回 null
     */
    UserSessionDTO getSession(String sessionId);

    /**
     * 查询用户当前全部有效会话
     *
     * @param userId 用户ID
     * @return 会话列表（按登录时间升序）
     */
    List<UserSessionDTO> listSessions(String userId);

    /**
     * 原子消费 Refresh Token（轮换 + 重放检测）
     * <p>
     * 消费成功后旧令牌被标记为已使用但保留剩余 TTL，用于后续重放检测。
     * </p>
     *
     * @param refreshToken 待消费的 Refresh Token
     * @return 消费结果，含状态与原会话信息
     */
    RefreshConsumeResult consumeRefreshToken(String refreshToken);

    /**
     * 绑定新的 Refresh Token 到会话
     * <p>轮换成功后调用：写入新令牌映射，并同步更新会话详情中的 refreshToken 字段。</p>
     *
     * @param session      会话信息（需已设置新的 refreshToken）
     * @param ttlSeconds   新令牌有效期（秒）
     */
    void bindRefreshToken(UserSessionDTO session, long ttlSeconds);

    /**
     * 撤销单个会话
     * <p>删除会话详情、对应的 Refresh Token 映射，并从用户会话索引中移除。</p>
     *
     * @param sessionId 会话ID
     */
    void revokeSession(String sessionId);

    /**
     * 撤销用户全部会话（全端下线）
     * <p>触发场景：修改密码、管理员封号/踢人、检测到 Refresh Token 重放。</p>
     *
     * @param userId 用户ID
     * @return 被撤销的会话数量
     */
    int revokeAllSessions(String userId);

    /**
     * 将 Access Token 加入黑名单
     *
     * @param sessionId        会话ID（JWT 的 jti）
     * @param remainingSeconds 该令牌剩余有效期（秒），小于等于 0 时不写入
     * @param reason           撤销原因
     */
    void blacklistAccessToken(String sessionId, long remainingSeconds, String reason);

    /**
     * 判断 Access Token 是否已被撤销
     *
     * @param sessionId 会话ID（JWT 的 jti）
     * @return true=已撤销
     */
    boolean isBlacklisted(String sessionId);
}

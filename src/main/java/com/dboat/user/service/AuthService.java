package com.dboat.user.service;

import com.dboat.user.dto.request.ChangePasswordReqDTO;
import com.dboat.user.dto.request.LoginReqDTO;
import com.dboat.user.dto.request.LogoutReqDTO;
import com.dboat.user.dto.request.RefreshTokenReqDTO;
import com.dboat.user.dto.request.RegisterReqDTO;
import com.dboat.user.dto.request.SendCaptchaReqDTO;
import com.dboat.user.dto.response.CaptchaRespDTO;
import com.dboat.user.dto.response.CurrentUserRespDTO;
import com.dboat.user.dto.response.TokenRespDTO;

/**
 * 认证鉴权业务服务
 * <p>
 * 编排双令牌机制的完整生命周期：注册自动登录、密码/验证码登录、令牌轮换刷新、
 * 单端登出与全端下线、密码修改与强制失效。
 * </p>
 * <p>
 * 客户端 IP 与 User-Agent 通过 {@code AuthWebUtils.currentRequest()} 从请求上下文获取，
 * 使 Service 方法签名保持「仅接受 DTO」的项目统一规范。
 * </p>
 *
 * @author dboat
 */
public interface AuthService {

    /**
     * 用户注册（注册成功后自动登录并签发双令牌）
     * <p>
     * 流程：校验验证码 → 判定租户归属 → 唯一性校验 → 创建用户（默认角色 VIEWER）
     * → 平台首个用户自动创建租户并授予 ADMIN → 签发双令牌。
     * </p>
     *
     * @param request 注册请求
     * @return 双令牌信息
     */
    TokenRespDTO register(RegisterReqDTO request);

    /**
     * 用户登录
     * <p>
     * 流程：账号锁定检查 → 加载账号 → 账号状态检查 → 凭证比对（失败计数 +1，达阈值锁定）
     * → 签发双令牌 → 写入 Redis 会话 → 并发会话裁剪 → 异步写登录日志。
     * </p>
     *
     * @param request 登录请求
     * @return 双令牌信息
     */
    TokenRespDTO login(LoginReqDTO request);

    /**
     * 刷新令牌（Refresh Token 轮换）
     * <p>
     * 每次刷新发放新的 Access Token 与 Refresh Token，旧 Refresh Token 立即作废；
     * 检测到已作废令牌被重放时，判定凭证泄漏，撤销该用户全部会话并自增令牌版本号。
     * </p>
     *
     * @param request 刷新请求
     * @return 新的双令牌信息
     */
    TokenRespDTO refresh(RefreshTokenReqDTO request);

    /**
     * 登出当前会话
     * <p>
     * 撤销当前 Access Token 对应的会话，并将其加入黑名单直至自然过期；
     * 显式传入 refreshToken 时可一并作废指定的 Refresh Token。接口幂等，重复调用不报错。
     * </p>
     *
     * @param request 登出请求
     */
    void logout(LogoutReqDTO request);

    /**
     * 全端下线（撤销当前用户的全部会话）
     * <p>
     * 自增 {@code user.token_version} 使所有已签发令牌立即失效，适用于怀疑凭证泄漏的场景。
     * </p>
     *
     * @return 被撤销的会话数量
     */
    int logoutAll();

    /**
     * 修改密码
     * <p>
     * 改密成功后自增密码版本与令牌版本，撤销全部会话，强制所有端重新登录；
     * 若账号处于首次登录强制改密状态，同时清除 {@code first_login} 标记。
     * </p>
     *
     * @param request 改密请求
     */
    void changePassword(ChangePasswordReqDTO request);

    /**
     * 查询当前登录用户信息（含角色与权限集合）
     *
     * @return 当前用户信息
     */
    CurrentUserRespDTO currentUser();

    /**
     * 发送验证码
     *
     * @param request 发送请求
     * @return 发送结果
     */
    CaptchaRespDTO sendCaptcha(SendCaptchaReqDTO request);
}

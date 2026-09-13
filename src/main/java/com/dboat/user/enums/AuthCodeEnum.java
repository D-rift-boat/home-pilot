package com.dboat.user.enums;

import com.dboat.iot.enums.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证鉴权业务错误码枚举
 * <p>
 * 与 {@link com.dboat.iot.dto.response.Result} 的 code 字段配合使用，
 * 由 {@link com.dboat.iot.exception.BusinessException} 携带抛出，
 * 最终经 {@link com.dboat.iot.exception.GlobalExceptionHandler} 统一转换响应。
 * </p>
 * <p>
 * 码值分段规范：
 * <ul>
 *   <li>401 / 403 —— 对齐 HTTP 语义的未认证与无权限</li>
 *   <li>401xx —— 令牌相关错误（过期、无效、撤销、重放）</li>
 *   <li>402xx —— 账号与凭证相关错误（密码、锁定、验证码、租户）</li>
 *   <li>403xx —— 会话相关错误</li>
 *   <li>429xx —— 限流</li>
 * </ul>
 * 安全约束：账号不存在与密码错误统一返回同一提示，避免账号枚举攻击。
 * </p>
 *
 * @author dboat
 */
@Getter
@AllArgsConstructor
public enum AuthCodeEnum implements CodeEnum {

    // ==================== 认证 / 授权 ====================

    /** 未携带令牌或令牌解析失败 */
    UNAUTHORIZED(401, "未认证或令牌缺失", "Unauthorized"),

    /** 已认证但缺少目标资源权限 */
    FORBIDDEN(403, "无访问权限", "Forbidden"),

    // ==================== 令牌类错误 401xx ====================

    /** Access Token 已过期，前端应使用 Refresh Token 换取新令牌 */
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录", "Access token expired"),

    /** Access Token 签名无效或格式非法 */
    TOKEN_INVALID(40102, "令牌无效", "Invalid token"),

    /** Access Token 已被撤销（登出 / 踢人 / 令牌版本变更） */
    TOKEN_REVOKED(40103, "登录状态已失效，请重新登录", "Token revoked"),

    /** Refresh Token 不存在或已过期 */
    REFRESH_TOKEN_INVALID(40104, "刷新令牌无效或已过期", "Invalid refresh token"),

    /** Refresh Token 重放，判定令牌泄漏，已撤销该用户全部会话 */
    REFRESH_TOKEN_REPLAY(40105, "检测到登录凭证异常，已强制下线全部设备", "Refresh token replay detected"),

    // ==================== 账号与凭证类错误 402xx ====================

    /** 账号不存在（对外统一为"账号或密码错误"，防账号枚举） */
    ACCOUNT_NOT_FOUND(40201, "账号或密码错误", "Account not found"),

    /** 密码错误（对外统一为"账号或密码错误"，防账号枚举） */
    ACCOUNT_PASSWORD_ERROR(40202, "账号或密码错误", "Incorrect password"),

    /** 账号被管理员禁用 */
    ACCOUNT_DISABLED(40203, "账号已被禁用，请联系管理员", "Account disabled"),

    /** 账号因连续密码错误被锁定 */
    ACCOUNT_LOCKED(40204, "账号已锁定，请稍后再试", "Account locked"),

    /** 验证码错误或已过期 */
    CAPTCHA_INVALID(40205, "验证码错误或已过期", "Invalid captcha"),

    /** 验证码发送过于频繁 */
    CAPTCHA_SEND_TOO_FREQUENT(40206, "验证码发送过于频繁，请稍后再试", "Captcha sent too frequently"),

    /** 注册标识已存在 */
    IDENTIFIER_DUPLICATED(40207, "该手机号/邮箱或账号已被注册", "Identifier duplicated"),

    /** 多租户下 identifier 命中多个账号，需显式指定 orgId */
    ORG_REQUIRED(40208, "存在多个同名账号，请指定所属租户", "Org id required"),

    /** 租户不存在或已禁用 */
    ORG_NOT_FOUND(40209, "租户不存在或已禁用", "Org not found"),

    /** 注册需要租户邀请（平台已有用户且未指定 orgId） */
    ORG_INVITE_REQUIRED(40210, "注册需指定邀请租户，请联系管理员获取租户ID", "Org invite required"),

    /** 原密码不正确 */
    OLD_PASSWORD_ERROR(40211, "原密码不正确", "Old password incorrect"),

    /** 新密码与原密码相同 */
    SAME_PASSWORD(40212, "新密码不能与原密码相同", "Same password"),

    /** 目标标识格式非法（非手机号且非邮箱） */
    IDENTIFIER_INVALID(40213, "手机号或邮箱格式不正确", "Invalid identifier"),

    // ==================== 会话类错误 403xx ====================

    /** 登录会话不存在或已过期 */
    SESSION_NOT_FOUND(40301, "登录会话不存在或已过期", "Session not found"),

    // ==================== 限流 429xx ====================

    /** 触发接口限流 */
    RATE_LIMITED(42901, "请求过于频繁，请稍后再试", "Too many requests");

    /** 业务错误码 */
    private final int code;

    /** 中文提示信息，直接对外返回 */
    private final String nameCn;

    /** 英文提示信息，用于日志与国际化 */
    private final String nameEn;
}

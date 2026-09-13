package com.dboat.user.service;

/**
 * 认证接口限流服务
 * <p>
 * 登录、注册、刷新令牌、发送验证码等匿名接口是暴力破解与短信轰炸的主要入口，
 * 必须在网关/拦截器层做前置限流。此处采用 Redis + Lua 的固定窗口计数：
 * {@code auth:limit:{uri}:{clientIp}} 在 {@code auth.rate-limit.window-seconds} 内
 * 累计请求数，超过 {@code auth.rate-limit.max-requests} 即拒绝。
 * </p>
 * <p>
 * 计数与首次设置过期时间通过 Lua 原子完成，避免高并发下 INCR 与 EXPIRE 分离导致的窗口永不过期。
 * </p>
 *
 * @author dboat
 */
public interface ApiRateLimitService {

    /**
     * 尝试获取一次请求配额
     *
     * @param uri      请求 URI，作为限流维度之一
     * @param clientIp 客户端 IP，作为限流维度之一
     * @return true=放行；false=已触发限流，应拒绝请求
     */
    boolean tryAcquire(String uri, String clientIp);

    /**
     * 获取当前限流窗口的剩余秒数
     * <p>触发限流后用于填充响应的 Retry-After 提示。</p>
     *
     * @param uri      请求 URI
     * @param clientIp 客户端 IP
     * @return 剩余秒数，未处于限流窗口时返回 0
     */
    long windowRemainSeconds(String uri, String clientIp);
}

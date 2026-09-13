package com.dboat.user.service;

/**
 * 登录失败防护服务（失败计数 + 账号锁定）
 * <p>
 * 防暴力破解策略：以「租户 + 登录标识」为维度在 Redis 中累计连续失败次数，
 * 达到 {@code auth.login.max-fail-count} 阈值后写入锁定标记，
 * 锁定时长 {@code auth.login.lock-minutes}，到期自动解锁。
 * </p>
 * <p>
 * 计数与锁定写入通过 Lua 脚本原子完成，避免并发绕过阈值判定。
 * 数据库 {@code user.login_fail_count} / {@code user.lock_expire_time} 同步落库，
 * 用于审计与 Redis 数据丢失后的兜底判定。
 * </p>
 *
 * @author dboat
 */
public interface LoginAttemptService {

    /**
     * 构建失败计数维度 Key
     *
     * @param orgId      租户ID，可为空（未知租户时退化为仅按标识计数）
     * @param identifier 登录标识
     * @return 维度 Key
     */
    String buildAttemptKey(String orgId, String identifier);

    /**
     * 判断账号是否处于锁定状态
     *
     * @param attemptKey 维度 Key
     * @return true=已锁定
     */
    boolean isLocked(String attemptKey);

    /**
     * 获取锁定剩余秒数
     *
     * @param attemptKey 维度 Key
     * @return 剩余秒数，未锁定时返回 0
     */
    long lockRemainSeconds(String attemptKey);

    /**
     * 记录一次登录失败，必要时触发锁定
     *
     * @param attemptKey 维度 Key
     * @return true=本次失败已触发账号锁定
     */
    boolean recordFailure(String attemptKey);

    /**
     * 获取当前连续失败次数
     *
     * @param attemptKey 维度 Key
     * @return 失败次数
     */
    int currentFailCount(String attemptKey);

    /**
     * 清除失败计数与锁定标记（登录成功后调用）
     *
     * @param attemptKey 维度 Key
     */
    void clear(String attemptKey);
}

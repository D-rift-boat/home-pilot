package com.dboat.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dboat.user.entity.AuthLoginLog;
import com.dboat.user.enums.LoginChannelEnum;
import com.dboat.user.enums.LoginResultEnum;
import com.dboat.user.enums.LoginTypeEnum;

/**
* @author tanghj
* @description 针对表【auth_login_log(登录审计日志表)】的数据库操作Service
* @createDate 2026-09-13 18:29:59
*/
public interface AuthLoginLogService extends IService<AuthLoginLog> {

    /**
     * 异步记录登录审计日志
     * <p>
     * 登录日志属于旁路数据，投递到通用线程池异步落库，
     * 避免磁盘 IO 抖动拖慢登录主链路；落库失败仅告警，不影响登录结果。
     * </p>
     *
     * @param userId     用户ID，登录失败且账号不存在时为 null
     * @param identifier 登录标识（账号/手机号/邮箱）
     * @param loginType  登录类型
     * @param channel    登录渠道
     * @param loginIp    客户端IP
     * @param userAgent  客户端 UA
     * @param result     登录结果
     * @param failReason 失败原因，成功时为 null
     */
    void recordAsync(String userId, String identifier, LoginTypeEnum loginType, LoginChannelEnum channel,
                     String loginIp, String userAgent, LoginResultEnum result, String failReason);
}

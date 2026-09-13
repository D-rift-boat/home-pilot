package com.dboat.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dboat.user.entity.AuthLoginLog;
import com.dboat.user.enums.LoginChannelEnum;
import com.dboat.user.enums.LoginResultEnum;
import com.dboat.user.enums.LoginTypeEnum;
import com.dboat.user.mapper.AuthLoginLogMapper;
import com.dboat.user.service.AuthLoginLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

/**
* @author tanghj
* @description 针对表【auth_login_log(登录审计日志表)】的数据库操作Service实现
* @createDate 2026-09-13 18:29:59
*/
@Slf4j
@Service
public class AuthLoginLogServiceImpl extends ServiceImpl<AuthLoginLogMapper, AuthLoginLog>
    implements AuthLoginLogService{

    /** UA 字段数据库长度上限，超长需截断 */
    private static final int USER_AGENT_MAX_LENGTH = 255;

    /** 失败原因字段数据库长度上限，超长需截断 */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    /** 通用业务线程池，登录日志异步落库使用 */
    @Resource(name = "commonExecutor")
    private ExecutorService commonExecutor;

    /**
     * 异步记录登录审计日志
     *
     * @param userId     用户ID
     * @param identifier 登录标识
     * @param loginType  登录类型
     * @param channel    登录渠道
     * @param loginIp    客户端IP
     * @param userAgent  客户端 UA
     * @param result     登录结果
     * @param failReason 失败原因
     */
    @Override
    public void recordAsync(String userId, String identifier, LoginTypeEnum loginType, LoginChannelEnum channel,
                            String loginIp, String userAgent, LoginResultEnum result, String failReason) {
        AuthLoginLog loginLog = new AuthLoginLog();
        loginLog.setUserId(userId);
        loginLog.setIdentifier(identifier);
        loginLog.setLoginType(loginType == null ? LoginTypeEnum.PASSWORD.getCode() : loginType.getCode());
        loginLog.setChannel(channel == null ? LoginChannelEnum.WEB.getCode() : channel.getCode());
        loginLog.setLoginIp(loginIp);
        loginLog.setUserAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH));
        loginLog.setResult(result == null ? LoginResultEnum.FAIL.getCode() : result.getCode());
        loginLog.setFailReason(truncate(failReason, FAIL_REASON_MAX_LENGTH));
        loginLog.setCreateTime(LocalDateTime.now());

        // 旁路数据异步落库，失败仅告警，不影响登录主链路
        commonExecutor.execute(() -> {
            try {
                save(loginLog);
            } catch (Exception e) {
                log.warn("【登录日志】异步落库失败 identifier={}, result={}, reason={}",
                        identifier, loginLog.getResult(), e.getMessage());
            }
        });
    }

    /**
     * 按数据库字段长度截断字符串
     *
     * @param value     原始值
     * @param maxLength 最大长度
     * @return 截断后的值，入参为 null 时返回 null
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

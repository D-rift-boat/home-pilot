package com.dboat.user.service.impl;

import com.dboat.user.common.constants.AuthLuaConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.dto.response.CaptchaRespDTO;
import com.dboat.user.enums.AuthCodeEnum;
import com.dboat.user.enums.CaptchaSceneEnum;
import com.dboat.user.exception.AuthException;
import com.dboat.user.service.CaptchaService;
import com.dboat.user.utils.AuthWebUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_CAPTCHA;
import static com.dboat.user.common.constants.AuthRedisKeys.AUTH_CAPTCHA_INTERVAL;

/**
 * 验证码服务实现
 * <p>
 * 短信 / 邮件通道尚未接入，当前统一走「模拟下发」：验证码写入 Redis 并输出到日志，
 * {@code auth.captcha.mock-enabled=true} 时同时在响应中回传，便于本地联调。
 * 生产接入通道后只需替换 {@link #dispatch} 的实现，其余流程无需改动。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    /** Lua 脚本：验证码一次性校验（读取-比对-删除原子完成） */
    private final DefaultRedisScript<Long> verifyCaptchaScript =
            new DefaultRedisScript<>(AuthLuaConstants.LUA_VERIFY_CAPTCHA, Long.class);

    /** 强随机数发生器，避免验证码被预测 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** Redis 操作模板 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /**
     * 发送验证码
     *
     * @param scene      业务场景标识
     * @param identifier 接收目标：手机号或邮箱
     * @return 发送结果
     */
    @Override
    public CaptchaRespDTO sendCaptcha(String scene, String identifier) {
        CaptchaSceneEnum sceneEnum = CaptchaSceneEnum.fromScene(scene);
        if (sceneEnum == null) {
            throw new AuthException(AuthCodeEnum.CAPTCHA_INVALID, "验证码场景不合法");
        }
        if (!AuthWebUtils.isMobile(identifier) && !AuthWebUtils.isEmail(identifier)) {
            throw new AuthException(AuthCodeEnum.IDENTIFIER_INVALID);
        }

        AuthProperties.Captcha captcha = authProperties.getCaptcha();
        String intervalKey = String.format(AUTH_CAPTCHA_INTERVAL, sceneEnum.getScene(), identifier);

        // ===== 发送间隔防刷：SETNX 成功才允许下发，天然规避并发绕过 =====
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(intervalKey, "1",
                captcha.getSendIntervalSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            Long remain = stringRedisTemplate.getExpire(intervalKey, TimeUnit.SECONDS);
            log.warn("【验证码】发送过于频繁 scene={}, target={}, 剩余 {}s",
                    sceneEnum.getScene(), AuthWebUtils.maskIdentifier(identifier), remain);
            throw new AuthException(AuthCodeEnum.CAPTCHA_SEND_TOO_FREQUENT);
        }

        String code = generateCode(captcha.getLength());
        stringRedisTemplate.opsForValue().set(String.format(AUTH_CAPTCHA, sceneEnum.getScene(), identifier),
                code, captcha.getTtlSeconds(), TimeUnit.SECONDS);

        // ===== 下发验证码（当前为模拟通道） =====
        dispatch(sceneEnum, identifier, code);

        return CaptchaRespDTO.builder()
                .scene(sceneEnum.getScene())
                .identifier(AuthWebUtils.maskIdentifier(identifier))
                .expireSeconds(captcha.getTtlSeconds())
                .sendIntervalSeconds(captcha.getSendIntervalSeconds())
                // 仅开发环境回传明文，生产环境为 null
                .mockCode(captcha.isMockEnabled() ? code : null)
                .build();
    }

    /**
     * 校验验证码（一次性）
     *
     * @param scene      业务场景标识
     * @param identifier 接收目标
     * @param code       用户提交的验证码
     * @return true=校验通过
     */
    @Override
    public boolean verify(String scene, String identifier, String code) {
        CaptchaSceneEnum sceneEnum = CaptchaSceneEnum.fromScene(scene);
        if (sceneEnum == null || !StringUtils.hasText(identifier) || !StringUtils.hasText(code)) {
            return false;
        }
        Long result = stringRedisTemplate.execute(verifyCaptchaScript,
                Collections.singletonList(String.format(AUTH_CAPTCHA, sceneEnum.getScene(), identifier)),
                code.trim());
        boolean pass = result != null && result == 1L;
        if (!pass) {
            log.warn("【验证码】校验失败 scene={}, target={}",
                    sceneEnum.getScene(), AuthWebUtils.maskIdentifier(identifier));
        }
        return pass;
    }

    /**
     * 主动作废验证码
     *
     * @param scene      业务场景标识
     * @param identifier 接收目标
     */
    @Override
    public void invalidate(String scene, String identifier) {
        CaptchaSceneEnum sceneEnum = CaptchaSceneEnum.fromScene(scene);
        if (sceneEnum == null || !StringUtils.hasText(identifier)) {
            return;
        }
        stringRedisTemplate.delete(String.format(AUTH_CAPTCHA, sceneEnum.getScene(), identifier));
    }

    // ==================== 内部工具 ====================

    /**
     * 生成指定位数的纯数字验证码
     *
     * @param length 验证码长度
     * @return 验证码明文
     */
    private String generateCode(int length) {
        int size = length <= 0 ? 6 : length;
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 下发验证码
     * <p>
     * 当前未接入真实短信 / 邮件通道：无论 mock 开关如何都会打印日志（便于排查），
     * mock 关闭时额外提示通道缺失。接入通道后在此处替换为真实的 SMS / Mail 调用即可。
     * </p>
     *
     * @param sceneEnum  业务场景
     * @param identifier 接收目标
     * @param code       验证码明文
     */
    private void dispatch(CaptchaSceneEnum sceneEnum, String identifier, String code) {
        boolean mock = authProperties.getCaptcha().isMockEnabled();
        String channel = AuthWebUtils.isEmail(identifier) ? "邮箱" : "短信";
        if (mock) {
            log.info("【验证码-模拟下发】scene={}, channel={}, target={}, code={}",
                    sceneEnum.getScene(), channel, AuthWebUtils.maskIdentifier(identifier), code);
            return;
        }
        // 生产环境必须在此接入真实通道，否则用户永远收不到验证码
        log.error("【验证码】{}通道尚未接入，验证码无法送达 scene={}, target={}",
                channel, sceneEnum.getScene(), AuthWebUtils.maskIdentifier(identifier));
    }
}

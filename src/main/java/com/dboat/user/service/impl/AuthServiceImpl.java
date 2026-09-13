package com.dboat.user.service.impl;

import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.dto.request.ChangePasswordReqDTO;
import com.dboat.user.dto.request.LoginReqDTO;
import com.dboat.user.dto.request.LogoutReqDTO;
import com.dboat.user.dto.request.RefreshTokenReqDTO;
import com.dboat.user.dto.request.RegisterReqDTO;
import com.dboat.user.dto.request.SendCaptchaReqDTO;
import com.dboat.user.dto.response.CaptchaRespDTO;
import com.dboat.user.dto.response.CurrentUserRespDTO;
import com.dboat.user.dto.response.TokenRespDTO;
import com.dboat.user.dto.session.RefreshConsumeResult;
import com.dboat.user.dto.session.UserSessionDTO;
import com.dboat.user.entity.Role;
import com.dboat.user.entity.User;
import com.dboat.user.entity.UserAuth;
import com.dboat.user.entity.UserOrg;
import com.dboat.user.entity.UserRole;
import com.dboat.user.enums.AuthCodeEnum;
import com.dboat.user.enums.AuthTypeEnum;
import com.dboat.user.enums.CaptchaSceneEnum;
import com.dboat.user.enums.CommonStatusEnum;
import com.dboat.user.enums.LoginChannelEnum;
import com.dboat.user.enums.LoginResultEnum;
import com.dboat.user.enums.LoginTypeEnum;
import com.dboat.user.enums.UserStatusEnum;
import com.dboat.user.exception.AuthException;
import com.dboat.user.security.AuthUserDetailsService;
import com.dboat.user.security.JwtTokenProvider;
import com.dboat.user.security.LoginUser;
import com.dboat.user.security.SecurityUserContext;
import com.dboat.user.service.AuthLoginLogService;
import com.dboat.user.service.AuthService;
import com.dboat.user.service.CaptchaService;
import com.dboat.user.service.LoginAttemptService;
import com.dboat.user.service.RolePermService;
import com.dboat.user.service.RoleService;
import com.dboat.user.service.TokenSessionService;
import com.dboat.user.service.UserAuthorityService;
import com.dboat.user.service.UserAuthService;
import com.dboat.user.service.UserOrgService;
import com.dboat.user.service.UserRoleService;
import com.dboat.user.service.UserService;
import com.dboat.user.utils.AuthWebUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 认证鉴权业务服务实现
 * <p>
 * 完整落地生产级双令牌机制：
 * <ul>
 *   <li><b>注册</b>：验证码 → 租户归属判定（平台首个用户自动建租户并授予 ADMIN）→ 唯一性校验
 *       → 创建 user / user_auth / user_role → 自动登录签发双令牌</li>
 *   <li><b>登录</b>：锁定检查 → 加载账号 → 状态检查 → 凭证比对（失败计数 + 达阈值锁定）
 *       → 签发双令牌 → 写 Redis 会话 → 并发会话裁剪 → 异步审计</li>
 *   <li><b>刷新</b>：Lua 原子消费旧 Refresh Token → 重放检测（命中即全端下线 + 令牌版本 +1）
 *       → 轮换发放新 Refresh Token → 签发新 Access Token</li>
 *   <li><b>登出</b>：会话撤销 + Access Token 黑名单（自然过期前主动失效）</li>
 *   <li><b>改密</b>：密码版本 + 令牌版本自增 → 全端强制下线</li>
 * </ul>
 * </p>
 * <p>
 * 账号枚举防护：账号不存在与密码错误统一返回 {@code 40201/40202}（提示语一致），
 * 且两种情况都会累计失败计数，使响应时间与行为特征无法被区分。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /** BCrypt 密码编码器 */
    @Resource
    private PasswordEncoder passwordEncoder;

    /** JWT 令牌提供者 */
    @Resource
    private JwtTokenProvider jwtTokenProvider;

    /** 认证用户加载服务 */
    @Resource
    private AuthUserDetailsService authUserDetailsService;

    /** 用户主表服务 */
    @Resource
    private UserService userService;

    /** 用户认证凭据服务 */
    @Resource
    private UserAuthService userAuthService;

    /** 用户角色关联服务 */
    @Resource
    private UserRoleService userRoleService;

    /** 角色服务 */
    @Resource
    private RoleService roleService;

    /** 角色权限关联服务 */
    @Resource
    private RolePermService rolePermService;

    /** 租户服务 */
    @Resource
    private UserOrgService userOrgService;

    /** 角色 / 权限 / 版本号缓存服务 */
    @Resource
    private UserAuthorityService userAuthorityService;

    /** 会话与 Refresh Token 管理服务 */
    @Resource
    private TokenSessionService tokenSessionService;

    /** 登录失败防护服务 */
    @Resource
    private LoginAttemptService loginAttemptService;

    /** 验证码服务 */
    @Resource
    private CaptchaService captchaService;

    /** 登录审计日志服务 */
    @Resource
    private AuthLoginLogService authLoginLogService;

    // ==================== 注册 ====================

    /**
     * 用户注册（成功后自动登录）
     *
     * @param request 注册请求
     * @return 双令牌信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenRespDTO register(RegisterReqDTO request) {
        String identifier = request.getIdentifier().trim();

        // ===== 1. 校验注册验证码（一次性，校验通过即失效） =====
        if (!captchaService.verify(CaptchaSceneEnum.REGISTER.getScene(), identifier, request.getCaptchaCode())) {
            throw new AuthException(AuthCodeEnum.CAPTCHA_INVALID);
        }
        boolean isEmail = AuthWebUtils.isEmail(identifier);
        if (!isEmail && !AuthWebUtils.isMobile(identifier)) {
            throw new AuthException(AuthCodeEnum.IDENTIFIER_INVALID);
        }

        // ===== 2. 判定租户归属与默认角色 =====
        // 平台首个用户：自动创建租户并授予 ADMIN；其余用户必须携带邀请租户，默认授予 VIEWER
        boolean firstPlatformUser = userService.countPlatformUsers() == 0;
        String orgId;
        String roleCode;
        if (StringUtils.hasText(request.getOrgId())) {
            UserOrg org = userOrgService.getEnabledOrg(request.getOrgId().trim());
            if (org == null) {
                throw new AuthException(AuthCodeEnum.ORG_NOT_FOUND);
            }
            orgId = org.getOrgId();
            roleCode = AuthConstants.ROLE_VIEWER;
        } else if (firstPlatformUser) {
            UserOrg org = userOrgService.createOrg(null, request.getOrgName());
            orgId = org.getOrgId();
            roleCode = AuthConstants.ROLE_ADMIN;
            log.info("【注册】平台首个用户，自动创建租户并授予 ADMIN orgId={}", orgId);
        } else {
            throw new AuthException(AuthCodeEnum.ORG_INVITE_REQUIRED);
        }

        // ===== 3. 唯一性校验：登录账号 + 认证标识（租户内唯一） =====
        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : identifier;
        if (userService.existsUsername(orgId, username)) {
            throw new AuthException(AuthCodeEnum.IDENTIFIER_DUPLICATED, "登录账号已被占用");
        }
        if (userAuthService.existsIdentifier(orgId, identifier, AuthTypeEnum.PASSWORD.getCode())) {
            throw new AuthException(AuthCodeEnum.IDENTIFIER_DUPLICATED, "该手机号/邮箱已被注册");
        }

        // ===== 4. 内置角色兜底初始化（新租户首次注册时自动补齐 ADMIN/OPERATOR/VIEWER） =====
        Role role = ensureBuiltinRoles(orgId, roleCode);

        // ===== 5. 落库：用户主表 + 密码凭据 + 角色关联 =====
        LocalDateTime now = LocalDateTime.now();
        String userId = uuid();
        userService.save(buildUser(userId, orgId, username, request, identifier, isEmail, now));
        userAuthService.save(buildPasswordAuth(orgId, userId, identifier, request.getPassword(), now));
        userRoleService.save(buildUserRole(orgId, userId, role.getRoleId(), now));

        // 清除可能存在的旧缓存，登录链路重新回源
        userAuthorityService.evictAll(userId);

        // ===== 6. 自动登录：签发双令牌 =====
        LoginUser loginUser = authUserDetailsService.loadByUserId(userId);
        if (loginUser == null) {
            throw new AuthException(AuthCodeEnum.ACCOUNT_NOT_FOUND);
        }
        LoginChannelEnum channel = LoginChannelEnum.fromCode(request.getChannel());
        String clientIp = AuthWebUtils.currentClientIp();
        String userAgent = AuthWebUtils.currentUserAgent();
        TokenRespDTO token = issueTokens(loginUser, clientIp, userAgent, channel, LoginTypeEnum.PASSWORD);

        authLoginLogService.recordAsync(userId, identifier, LoginTypeEnum.PASSWORD, channel,
                clientIp, userAgent, LoginResultEnum.SUCCESS, null);
        log.info("【注册成功】userId={}, orgId={}, username={}, roleCode={}, ip={}",
                userId, orgId, username, roleCode, clientIp);
        return token;
    }

    // ==================== 登录 ====================

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 双令牌信息
     */
    @Override
    public TokenRespDTO login(LoginReqDTO request) {
        String identifier = request.getIdentifier().trim();
        LoginTypeEnum loginType = LoginTypeEnum.fromCode(request.getLoginType());
        LoginChannelEnum channel = LoginChannelEnum.fromCode(request.getChannel());
        String clientIp = AuthWebUtils.currentClientIp();
        String userAgent = AuthWebUtils.currentUserAgent();
        String attemptKey = loginAttemptService.buildAttemptKey(request.getOrgId(), identifier);

        // ===== 1. 锁定前置检查：避免锁定期内仍执行 BCrypt 哈希计算 =====
        if (loginAttemptService.isLocked(attemptKey)) {
            long remainSeconds = loginAttemptService.lockRemainSeconds(attemptKey);
            authLoginLogService.recordAsync(null, identifier, loginType, channel, clientIp, userAgent,
                    LoginResultEnum.FAIL, AuthCodeEnum.ACCOUNT_LOCKED.getNameCn());
            throw new AuthException(AuthCodeEnum.ACCOUNT_LOCKED,
                    String.format("账号已锁定，请 %d 秒后重试", remainSeconds));
        }

        // ===== 2. 加载账号（账号 / 手机号 / 邮箱三种标识） =====
        LoginUser loginUser;
        try {
            loginUser = authUserDetailsService.loadByIdentifier(request.getOrgId(), identifier);
        } catch (AuthException e) {
            // 账号不存在同样计入失败次数，使响应特征与密码错误一致，防止账号枚举
            loginAttemptService.recordFailure(attemptKey);
            authLoginLogService.recordAsync(null, identifier, loginType, channel, clientIp, userAgent,
                    LoginResultEnum.FAIL, e.getMessage());
            throw e;
        }

        // ===== 3. 账号状态检查：禁用 / 锁定 =====
        checkAccountStatus(loginUser, identifier, loginType, channel, clientIp, userAgent);

        // ===== 4. 凭证比对：密码或验证码 =====
        AuthCodeEnum failReason = loginType == LoginTypeEnum.CAPTCHA
                ? AuthCodeEnum.CAPTCHA_INVALID
                : AuthCodeEnum.ACCOUNT_PASSWORD_ERROR;
        if (!verifyCredential(loginUser, request, loginType)) {
            throw buildLoginFailure(loginUser, attemptKey, identifier, loginType, channel,
                    clientIp, userAgent, failReason);
        }

        // ===== 5. 登录成功：清除失败计数、刷新登录信息、预热版本缓存 =====
        loginAttemptService.clear(attemptKey);
        userService.resetLoginFailState(loginUser.getUserId());
        userService.markLoginSuccess(loginUser.getUserId(), clientIp);
        userAuthorityService.cacheVersions(loginUser.getUserId(),
                loginUser.getTokenVersion(), loginUser.getPermVersion());

        // ===== 6. 签发双令牌并写入 Redis 会话（含并发会话裁剪） =====
        TokenRespDTO token = issueTokens(loginUser, clientIp, userAgent, channel, loginType);
        authLoginLogService.recordAsync(loginUser.getUserId(), identifier, loginType, channel,
                clientIp, userAgent, LoginResultEnum.SUCCESS, null);
        log.info("【登录成功】userId={}, orgId={}, ip={}, channel={}, loginType={}",
                loginUser.getUserId(), loginUser.getOrgId(), clientIp, channel.getNameCn(), loginType.getNameCn());
        return token;
    }

    // ==================== 令牌刷新 ====================

    /**
     * 刷新令牌（Refresh Token 轮换 + 重放检测）
     *
     * @param request 刷新请求
     * @return 新的双令牌信息
     */
    @Override
    public TokenRespDTO refresh(RefreshTokenReqDTO request) {
        RefreshConsumeResult consumeResult = tokenSessionService.consumeRefreshToken(request.getRefreshToken());

        // ===== 重放攻击：已作废的旧令牌被再次使用，判定凭证泄漏 =====
        if (consumeResult.isReplay()) {
            String leakedUserId = consumeResult.getUserId();
            log.error("【令牌安全】检测到 Refresh Token 重放，撤销用户全部会话并自增令牌版本 userId={}, orgId={}",
                    leakedUserId, consumeResult.getOrgId());
            if (StringUtils.hasText(leakedUserId)) {
                tokenSessionService.revokeAllSessions(leakedUserId);
                userService.increaseTokenVersion(leakedUserId);
                userAuthorityService.evictAll(leakedUserId);
            }
            throw new AuthException(AuthCodeEnum.REFRESH_TOKEN_REPLAY);
        }
        if (!consumeResult.isSuccess()) {
            throw new AuthException(AuthCodeEnum.REFRESH_TOKEN_INVALID);
        }

        UserSessionDTO session = consumeResult.getSession();
        String userId = session.getUserId();
        String clientIp = AuthWebUtils.currentClientIp();

        // ===== 1. 复核账号存活与令牌版本（改密 / 封号后旧会话不可续期） =====
        User user = userService.getUserById(userId);
        if (user == null) {
            tokenSessionService.revokeSession(session.getSessionId());
            throw new AuthException(AuthCodeEnum.REFRESH_TOKEN_INVALID);
        }
        if (!Objects.equals(user.getTokenVersion(), session.getTokenVersion())) {
            log.warn("【令牌刷新】令牌版本已变更，拒绝续期并撤销会话 userId={}, {} -> {}",
                    userId, session.getTokenVersion(), user.getTokenVersion());
            tokenSessionService.revokeSession(session.getSessionId());
            throw new AuthException(AuthCodeEnum.TOKEN_REVOKED);
        }
        LoginUser loginUser = authUserDetailsService.loadByUserId(userId);
        if (loginUser == null) {
            tokenSessionService.revokeSession(session.getSessionId());
            throw new AuthException(AuthCodeEnum.REFRESH_TOKEN_INVALID);
        }
        if (loginUser.getStatus() == null || loginUser.getStatus() != UserStatusEnum.NORMAL.getCode()) {
            log.warn("【令牌刷新】账号状态异常，撤销全部会话 userId={}, status={}", userId, loginUser.getStatus());
            tokenSessionService.revokeAllSessions(userId);
            throw new AuthException(AuthCodeEnum.ACCOUNT_DISABLED);
        }

        // ===== 2. 轮换：发放新 Refresh Token 并绑定到原会话（sessionId 不变，会话数不增加） =====
        long refreshTtlSeconds = jwtTokenProvider.getRefreshTokenTtlSeconds();
        LoginChannelEnum channel = LoginChannelEnum.fromCode(
                request.getChannel() == null ? session.getChannel() : request.getChannel());
        session.setRefreshToken(jwtTokenProvider.generateRefreshToken());
        session.setRoleKeys(new ArrayList<>(loginUser.getRoleKeys()));
        session.setTokenVersion(loginUser.getTokenVersion());
        session.setPermVersion(loginUser.getPermVersion());
        session.setChannel(channel.getCode());
        session.setExpireTs(System.currentTimeMillis() + refreshTtlSeconds * 1000L);
        tokenSessionService.bindRefreshToken(session, refreshTtlSeconds);

        // ===== 3. 签发新的 Access Token =====
        loginUser.setSessionId(session.getSessionId());
        loginUser.setLoginTs(session.getLoginTs());
        loginUser.setLoginIp(session.getLoginIp());
        String accessToken = jwtTokenProvider.generateAccessToken(loginUser, session.getSessionId());
        userAuthorityService.cacheVersions(userId, loginUser.getTokenVersion(), loginUser.getPermVersion());

        log.info("【令牌刷新】轮换完成 userId={}, sessionId={}, ip={}", userId, session.getSessionId(), clientIp);
        return buildTokenResponse(loginUser, session.getSessionId(), accessToken,
                session.getRefreshToken(), needForceChangePassword(loginUser.getFirstLogin()));
    }

    // ==================== 登出 ====================

    /**
     * 登出当前会话
     *
     * @param request 登出请求
     */
    @Override
    public void logout(LogoutReqDTO request) {
        LoginUser loginUser = SecurityUserContext.getLoginUser();
        String refreshToken = request == null ? null : request.getRefreshToken();

        // ===== 场景一：显式传入 Refresh Token（Access Token 已过期时的兜底登出） =====
        if (StringUtils.hasText(refreshToken)) {
            RefreshConsumeResult result = tokenSessionService.consumeRefreshToken(refreshToken);
            if (result.isSuccess()) {
                UserSessionDTO session = result.getSession();
                revokeWithBlacklist(session.getSessionId(), session.getLoginTs());
                log.info("【登出】按 Refresh Token 撤销会话 userId={}, sessionId={}",
                        session.getUserId(), session.getSessionId());
                return;
            }
        }

        // ===== 场景二：按当前 Access Token 对应的会话登出 =====
        if (loginUser == null || !StringUtils.hasText(loginUser.getSessionId())) {
            // 幂等处理：会话不存在视为已登出，不向前端抛错
            return;
        }
        revokeWithBlacklist(loginUser.getSessionId(), loginUser.getLoginTs());
        log.info("【登出】userId={}, sessionId={}", loginUser.getUserId(), loginUser.getSessionId());
    }

    /**
     * 全端下线
     *
     * @return 被撤销的会话数量
     */
    @Override
    public int logoutAll() {
        LoginUser loginUser = requireCurrentUser();
        String userId = loginUser.getUserId();
        int revoked = tokenSessionService.revokeAllSessions(userId);
        // 令牌版本 +1：使所有尚未自然过期的 Access Token 立即失效
        userService.increaseTokenVersion(userId);
        userAuthorityService.evictAll(userId);
        log.info("【全端下线】userId={}, 撤销会话数={}", userId, revoked);
        return revoked;
    }

    // ==================== 修改密码 ====================

    /**
     * 修改密码（改密后全端强制下线）
     *
     * @param request 改密请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReqDTO request) {
        LoginUser loginUser = requireCurrentUser();
        String userId = loginUser.getUserId();

        UserAuth userAuth = userAuthService.getPasswordAuthByUserId(userId);
        if (userAuth == null || !StringUtils.hasText(userAuth.getCredential())) {
            throw new AuthException(AuthCodeEnum.OLD_PASSWORD_ERROR, "当前账号未设置密码");
        }
        // ===== 1. 原密码校验 =====
        if (!passwordEncoder.matches(request.getOldPassword(), userAuth.getCredential())) {
            throw new AuthException(AuthCodeEnum.OLD_PASSWORD_ERROR);
        }
        // ===== 2. 新密码不得与原密码相同（新密码强度已由 DTO 的 @Pattern 校验） =====
        if (passwordEncoder.matches(request.getNewPassword(), userAuth.getCredential())) {
            throw new AuthException(AuthCodeEnum.SAME_PASSWORD);
        }

        // ===== 3. 更新凭据：password_version + 1 =====
        userAuthService.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));

        // ===== 4. 令牌版本 +1 并撤销全部会话，强制所有端重新登录 =====
        userService.increaseTokenVersion(userId);
        tokenSessionService.revokeAllSessions(userId);
        userAuthorityService.evictAll(userId);

        // ===== 5. 首次登录强制改密完成，清除标记 =====
        User user = userService.getUserById(userId);
        if (user != null && user.getFirstLogin() != null && user.getFirstLogin() == 1) {
            userService.clearFirstLoginFlag(userId);
        }
        log.info("【修改密码】密码已更新，全部会话已撤销 userId={}", userId);
    }

    // ==================== 当前用户 ====================

    /**
     * 查询当前登录用户信息
     *
     * @return 当前用户信息（含角色与权限集合）
     */
    @Override
    public CurrentUserRespDTO currentUser() {
        LoginUser loginUser = requireCurrentUser();
        String userId = loginUser.getUserId();
        String orgId = loginUser.getOrgId();

        User user = userService.getUserById(userId);
        if (user == null) {
            throw new AuthException(AuthCodeEnum.UNAUTHORIZED);
        }
        UserOrg org = userOrgService.getEnabledOrg(orgId);
        List<Role> roles = roleService.listRolesByUserId(orgId, userId);
        Set<String> perms = userAuthorityService.getPermCodes(orgId, userId);

        return CurrentUserRespDTO.builder()
                .userId(userId)
                .orgId(orgId)
                .orgName(org == null ? null : org.getOrgName())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .firstLogin(needForceChangePassword(user.getFirstLogin()))
                .roleKeys(roles.stream().map(Role::getRoleCode).distinct().toList())
                .roleNames(roles.stream().map(Role::getRoleName).distinct().toList())
                .perms(new ArrayList<>(perms))
                .lastLoginTime(toEpochMilli(user.getLastLoginTime()))
                .lastLoginIp(user.getLastLoginIp())
                .createTime(toEpochMilli(user.getCreateTime()))
                .build();
    }

    // ==================== 验证码 ====================

    /**
     * 发送验证码
     *
     * @param request 发送请求
     * @return 发送结果
     */
    @Override
    public CaptchaRespDTO sendCaptcha(SendCaptchaReqDTO request) {
        return captchaService.sendCaptcha(request.getScene(), request.getIdentifier().trim());
    }

    // ==================== 内部工具：令牌签发 ====================

    /**
     * 签发双令牌并创建 Redis 会话
     *
     * @param loginUser  认证主体
     * @param clientIp   客户端 IP
     * @param userAgent  客户端 UA
     * @param channel    登录渠道
     * @param loginType  登录类型
     * @return 双令牌响应
     */
    private TokenRespDTO issueTokens(LoginUser loginUser, String clientIp, String userAgent,
                                     LoginChannelEnum channel, LoginTypeEnum loginType) {
        String sessionId = jwtTokenProvider.generateSessionId();
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        long now = System.currentTimeMillis();
        loginUser.setSessionId(sessionId);
        loginUser.setLoginIp(clientIp);
        loginUser.setLoginTs(now);

        // 写入 Refresh Token 映射、会话详情、用户会话索引（含并发会话裁剪）
        UserSessionDTO session = tokenSessionService.createSession(loginUser, sessionId, refreshToken,
                clientIp, userAgent, channel.getCode(), loginType.getCode());

        String accessToken = jwtTokenProvider.generateAccessToken(loginUser, sessionId);
        // 预热版本号缓存，避免刷新后的首个业务请求回源数据库
        userAuthorityService.cacheVersions(loginUser.getUserId(),
                loginUser.getTokenVersion(), loginUser.getPermVersion());

        return buildTokenResponse(loginUser, session.getSessionId(), accessToken, refreshToken,
                needForceChangePassword(loginUser.getFirstLogin()));
    }

    /**
     * 组装双令牌响应
     *
     * @param loginUser   认证主体
     * @param sessionId   会话ID
     * @param accessToken Access Token
     * @param refreshToken Refresh Token
     * @param firstLogin  是否需要强制改密
     * @return 双令牌响应
     */
    private TokenRespDTO buildTokenResponse(LoginUser loginUser, String sessionId, String accessToken,
                                            String refreshToken, boolean firstLogin) {
        return TokenRespDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstants.TOKEN_TYPE_BEARER)
                .expiresIn(jwtTokenProvider.getAccessTokenTtlSeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshTokenTtlSeconds())
                .sessionId(sessionId)
                .userId(loginUser.getUserId())
                .orgId(loginUser.getOrgId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .roleKeys(new ArrayList<>(loginUser.getRoleKeys()))
                .firstLogin(firstLogin)
                .build();
    }

    /**
     * 撤销会话并将 Access Token 加入黑名单
     * <p>Access Token 是无状态的，登出后若不写黑名单，在自然过期前仍然可用。</p>
     *
     * @param sessionId  会话ID（即 JWT 的 jti）
     * @param issuedAtMs 令牌签发时间戳（毫秒）
     */
    private void revokeWithBlacklist(String sessionId, Long issuedAtMs) {
        tokenSessionService.blacklistAccessToken(sessionId, remainingAccessSeconds(issuedAtMs), "logout");
        tokenSessionService.revokeSession(sessionId);
    }

    /**
     * 计算 Access Token 的剩余有效期
     *
     * @param issuedAtMs 签发时间戳（毫秒）
     * @return 剩余秒数，已过期返回 0
     */
    private long remainingAccessSeconds(Long issuedAtMs) {
        long ttlMillis = jwtTokenProvider.getAccessTokenTtlSeconds() * 1000L;
        long issuedAt = issuedAtMs == null ? System.currentTimeMillis() : issuedAtMs;
        long remaining = (issuedAt + ttlMillis - System.currentTimeMillis()) / 1000L;
        return Math.max(remaining, 0L);
    }

    // ==================== 内部工具：登录校验 ====================

    /**
     * 校验账号状态
     *
     * @param loginUser  认证主体
     * @param identifier 登录标识
     * @param loginType  登录类型
     * @param channel    登录渠道
     * @param clientIp   客户端 IP
     * @param userAgent  客户端 UA
     */
    private void checkAccountStatus(LoginUser loginUser, String identifier, LoginTypeEnum loginType,
                                    LoginChannelEnum channel, String clientIp, String userAgent) {
        Integer status = loginUser.getStatus();
        if (status == null || status == UserStatusEnum.NORMAL.getCode()) {
            return;
        }
        AuthCodeEnum reason = status == UserStatusEnum.DISABLED.getCode()
                ? AuthCodeEnum.ACCOUNT_DISABLED
                : AuthCodeEnum.ACCOUNT_LOCKED;
        log.warn("【登录失败】账号状态异常 userId={}, status={}, ip={}", loginUser.getUserId(), status, clientIp);
        authLoginLogService.recordAsync(loginUser.getUserId(), identifier, loginType, channel,
                clientIp, userAgent, LoginResultEnum.FAIL, reason.getNameCn());
        throw new AuthException(reason);
    }

    /**
     * 校验登录凭证
     *
     * @param loginUser 认证主体
     * @param request   登录请求
     * @param loginType 登录类型
     * @return true=凭证正确
     */
    private boolean verifyCredential(LoginUser loginUser, LoginReqDTO request, LoginTypeEnum loginType) {
        if (loginType == LoginTypeEnum.CAPTCHA) {
            return captchaService.verify(CaptchaSceneEnum.LOGIN.getScene(),
                    request.getIdentifier().trim(), request.getCaptchaCode());
        }
        // 密码缺失或账号未设置密码凭据，统一视为密码错误
        if (!StringUtils.hasText(request.getPassword()) || !StringUtils.hasText(loginUser.getPassword())) {
            return false;
        }
        return passwordEncoder.matches(request.getPassword(), loginUser.getPassword());
    }

    /**
     * 处理登录失败：累计失败计数、按需锁定账号、写审计日志，并构建对外异常
     * <p>返回异常而非直接抛出，便于调用方以 {@code throw} 显式表达控制流。</p>
     *
     * @param loginUser  认证主体
     * @param attemptKey 失败计数维度 Key
     * @param identifier 登录标识
     * @param loginType  登录类型
     * @param channel    登录渠道
     * @param clientIp   客户端 IP
     * @param userAgent  客户端 UA
     * @param reason     失败原因码
     * @return 待抛出的认证异常
     */
    private AuthException buildLoginFailure(LoginUser loginUser, String attemptKey, String identifier,
                                            LoginTypeEnum loginType, LoginChannelEnum channel,
                                            String clientIp, String userAgent, AuthCodeEnum reason) {
        AuthProperties.Login login = authProperties.getLogin();
        // Lua 原子计数，达到阈值时写入锁定标记并清空计数
        boolean locked = loginAttemptService.recordFailure(attemptKey);
        int failCount = locked ? login.getMaxFailCount() : loginAttemptService.currentFailCount(attemptKey);

        LocalDateTime lockExpireTime = locked
                ? LocalDateTime.now().plusMinutes(login.getLockMinutes())
                : null;
        // 数据库同步落库，用于审计与 Redis 数据丢失后的兜底判定
        userService.markLoginFail(loginUser.getUserId(), failCount, lockExpireTime);
        authLoginLogService.recordAsync(loginUser.getUserId(), identifier, loginType, channel,
                clientIp, userAgent, LoginResultEnum.FAIL, reason.getNameCn());

        if (locked) {
            log.warn("【登录失败】密码连续错误 {} 次，账号锁定 {} 分钟 userId={}, ip={}",
                    login.getMaxFailCount(), login.getLockMinutes(), loginUser.getUserId(), clientIp);
            return new AuthException(AuthCodeEnum.ACCOUNT_LOCKED,
                    String.format("密码连续错误已达 %d 次，账号锁定 %d 分钟",
                            login.getMaxFailCount(), login.getLockMinutes()));
        }
        int remainAttempts = login.getMaxFailCount() - failCount;
        log.warn("【登录失败】凭证校验不通过 userId={}, 已失败 {} 次, ip={}",
                loginUser.getUserId(), failCount, clientIp);
        return remainAttempts > 0
                ? new AuthException(reason, reason.getNameCn() + String.format("，还可尝试 %d 次", remainAttempts))
                : new AuthException(reason);
    }

    /**
     * 获取当前登录用户，未认证时抛出 401
     *
     * @return 认证主体
     */
    private LoginUser requireCurrentUser() {
        LoginUser loginUser = SecurityUserContext.getLoginUser();
        if (loginUser == null || !StringUtils.hasText(loginUser.getUserId())) {
            throw new AuthException(AuthCodeEnum.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 是否需要强制修改密码
     *
     * @param firstLogin 首次登录标记
     * @return true=前端应强制跳转改密页
     */
    private boolean needForceChangePassword(Integer firstLogin) {
        return authProperties.getLogin().isForceChangePasswordOnFirstLogin()
                && firstLogin != null && firstLogin == 1;
    }

    // ==================== 内部工具：实体构建 ====================

    /**
     * 确保租户内置角色齐备并返回目标角色
     * <p>新租户首次注册时自动补齐 ADMIN / OPERATOR / VIEWER 三个内置角色。</p>
     *
     * @param orgId           租户ID
     * @param requiredRoleCode 本次注册需要授予的角色编码
     * @return 目标角色实体
     */
    private Role ensureBuiltinRoles(String orgId, String requiredRoleCode) {
        createRoleIfAbsent(orgId, AuthConstants.ROLE_ADMIN, AuthConstants.ROLE_NAME_ADMIN,
                "租户管理员，拥有租户内全部权限");
        createRoleIfAbsent(orgId, AuthConstants.ROLE_OPERATOR, AuthConstants.ROLE_NAME_OPERATOR,
                "运维操作员，可管理设备与下发指令");
        createRoleIfAbsent(orgId, AuthConstants.ROLE_VIEWER, AuthConstants.ROLE_NAME_VIEWER,
                "只读访客，仅可查看设备数据");

        Role role = roleService.getByOrgIdAndRoleCode(orgId, requiredRoleCode);
        if (role == null) {
            throw new AuthException(AuthCodeEnum.ORG_NOT_FOUND, "租户内置角色初始化失败");
        }
        return role;
    }

    /**
     * 角色不存在时创建
     *
     * @param orgId    租户ID
     * @param roleCode 角色编码
     * @param roleName 角色名称
     * @param remark   角色描述
     * @return 角色实体（已存在则返回原记录）
     */
    private Role createRoleIfAbsent(String orgId, String roleCode, String roleName, String remark) {
        Role exist = roleService.getByOrgIdAndRoleCode(orgId, roleCode);
        if (exist != null) {
            return exist;
        }
        LocalDateTime now = LocalDateTime.now();
        Role role = new Role();
        role.setRoleId(uuid());
        role.setOrgId(orgId);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRemark(remark);
        role.setStatus(CommonStatusEnum.ENABLED.getCode());
        role.setCreateTime(now);
        role.setUpdateTime(now);
        roleService.save(role);

        // 内置角色自动授予默认权限，避免 OPERATOR / VIEWER 建出来就是"零权限"空角色
        List<String> defaultPerms = AuthConstants.BUILTIN_ROLE_PERM_TEMPLATE.get(roleCode);
        if (!CollectionUtils.isEmpty(defaultPerms)) {
            int granted = rolePermService.grantPermCodes(orgId, role.getRoleId(), defaultPerms);
            log.info("【注册】租户内置角色初始化 orgId={}, roleCode={}, 默认权限数={}", orgId, roleCode, granted);
        } else {
            log.info("【注册】租户内置角色初始化 orgId={}, roleCode={}", orgId, roleCode);
        }
        return role;
    }

    /**
     * 构建用户主表实体
     *
     * @param userId     用户ID
     * @param orgId      租户ID
     * @param username   登录账号
     * @param request    注册请求
     * @param identifier 注册标识
     * @param isEmail    标识是否为邮箱
     * @param now        当前时间
     * @return 用户实体
     */
    private User buildUser(String userId, String orgId, String username, RegisterReqDTO request,
                           String identifier, boolean isEmail, LocalDateTime now) {
        User user = new User();
        user.setUserId(userId);
        user.setOrgId(orgId);
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        // 邮箱注册时同步冗余到主表，便于站内通知与资料展示
        user.setEmail(isEmail ? identifier : null);
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        user.setTokenVersion(0);
        user.setLoginFailCount(0);
        // 首次登录强制改密标记，与 DDL 默认值保持一致
        user.setFirstLogin(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    /**
     * 构建密码认证凭据实体
     *
     * @param orgId        租户ID
     * @param userId       用户ID
     * @param identifier   认证标识
     * @param rawPassword  明文密码
     * @param now          当前时间
     * @return 认证凭据实体
     */
    private UserAuth buildPasswordAuth(String orgId, String userId, String identifier,
                                       String rawPassword, LocalDateTime now) {
        UserAuth userAuth = new UserAuth();
        userAuth.setOrgId(orgId);
        userAuth.setUserId(userId);
        userAuth.setAuthType(AuthTypeEnum.PASSWORD.getCode());
        userAuth.setIdentifier(identifier);
        // BCrypt 自带随机盐，同一明文多次加密结果不同
        userAuth.setCredential(passwordEncoder.encode(rawPassword));
        userAuth.setPasswordVersion(0);
        userAuth.setLastPasswordChange(now);
        userAuth.setCreateTime(now);
        userAuth.setUpdateTime(now);
        return userAuth;
    }

    /**
     * 构建用户角色关联实体
     *
     * @param orgId  租户ID
     * @param userId 用户ID
     * @param roleId 角色ID
     * @param now    当前时间
     * @return 关联实体
     */
    private UserRole buildUserRole(String orgId, String userId, String roleId, LocalDateTime now) {
        UserRole userRole = new UserRole();
        userRole.setId(uuid());
        userRole.setOrgId(orgId);
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setCreateTime(now);
        return userRole;
    }

    // ==================== 内部工具：通用 ====================

    /**
     * 生成去横线的 UUID 主键
     *
     * @return UUID 字符串
     */
    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * LocalDateTime 转毫秒时间戳
     * <p>响应 DTO 统一使用时间戳，规避 LocalDateTime 被序列化为数组的问题。</p>
     *
     * @param dateTime 时间对象
     * @return 毫秒时间戳，入参为 null 时返回 null
     */
    private Long toEpochMilli(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

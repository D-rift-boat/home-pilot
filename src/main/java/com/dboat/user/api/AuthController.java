package com.dboat.user.api;

import com.dboat.iot.dto.response.Result;
import com.dboat.user.dto.request.AuthEmptyReqDTO;
import com.dboat.user.dto.request.ChangePasswordReqDTO;
import com.dboat.user.dto.request.LoginReqDTO;
import com.dboat.user.dto.request.LogoutReqDTO;
import com.dboat.user.dto.request.RefreshTokenReqDTO;
import com.dboat.user.dto.request.RegisterReqDTO;
import com.dboat.user.dto.request.SendCaptchaReqDTO;
import com.dboat.user.dto.response.CaptchaRespDTO;
import com.dboat.user.dto.response.CurrentUserRespDTO;
import com.dboat.user.dto.response.TokenRespDTO;
import com.dboat.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证鉴权 Controller
 * <p>
 * 提供注册、登录、令牌刷新、登出、全端下线、修改密码、当前用户与验证码下发接口。
 * 全部接口统一使用 POST + DTO 参数封装，响应统一为 {@link Result}。
 * </p>
 * <p>
 * 免认证接口（{@code auth.security.permit-all-paths}）：
 * {@code /register}、{@code /login}、{@code /refresh}、{@code /captcha/send}；
 * 其余接口必须携带 {@code Authorization: Bearer {accessToken}}。
 * </p>
 * <p>
 * 前端令牌使用约定：
 * <ul>
 *   <li>Access Token 过期（业务码 40101）→ 静默调用 {@code /refresh} 换取新双令牌并重放原请求</li>
 *   <li>Refresh Token 失效（40104）或被判定泄漏（40105）→ 跳转登录页</li>
 *   <li>{@code firstLogin=true} → 强制跳转修改密码页</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "用户注册 / 登录 / 令牌刷新 / 登出 / 鉴权 API")
public class AuthController {

    /** 认证业务服务 */
    private final AuthService authService;

    /**
     * 构造器注入认证服务
     *
     * @param authService 认证业务服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册（成功后自动登录并返回双令牌）
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "手机号/邮箱 + 验证码 + 密码注册，平台首个用户自动建租户并授予 ADMIN")
    public Result<TokenRespDTO> register(@Valid @RequestBody RegisterReqDTO request) {
        return Result.ok(authService.register(request));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "支持账号/手机号/邮箱 + 密码，或验证码登录；连续失败 5 次锁定 10 分钟")
    public Result<TokenRespDTO> login(@Valid @RequestBody LoginReqDTO request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 刷新令牌（Refresh Token 轮换）
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "轮换 Refresh Token 并签发新的双令牌；旧令牌重放将触发全端下线")
    public Result<TokenRespDTO> refresh(@Valid @RequestBody RefreshTokenReqDTO request) {
        return Result.ok(authService.refresh(request));
    }

    /**
     * 登出当前会话
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "撤销当前会话并将 Access Token 加入黑名单，接口幂等")
    public Result<Void> logout(@Valid @RequestBody(required = false) LogoutReqDTO request) {
        authService.logout(request);
        return Result.ok();
    }

    /**
     * 全端下线（撤销当前用户全部会话）
     */
    @PostMapping("/logoutAll")
    @Operation(summary = "全端下线", description = "撤销当前用户全部会话并自增令牌版本，所有已签发令牌立即失效")
    public Result<Integer> logoutAll(@Valid @RequestBody(required = false) AuthEmptyReqDTO request) {
        return Result.ok(authService.logoutAll());
    }

    /**
     * 修改密码（改密后全端强制下线）
     */
    @PostMapping("/changePassword")
    @Operation(summary = "修改密码", description = "校验原密码后更新，改密成功需重新登录")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReqDTO request) {
        authService.changePassword(request);
        return Result.ok();
    }

    /**
     * 查询当前登录用户信息
     */
    @PostMapping("/currentUser")
    @Operation(summary = "当前用户", description = "返回当前登录用户的资料、角色集合与权限编码集合")
    public Result<CurrentUserRespDTO> currentUser(@Valid @RequestBody(required = false) AuthEmptyReqDTO request) {
        return Result.ok(authService.currentUser());
    }

    /**
     * 发送验证码
     */
    @PostMapping("/captcha/send")
    @Operation(summary = "发送验证码", description = "支持 register/login/resetPwd/changePwd 四类场景，同一目标存在发送间隔限制")
    public Result<CaptchaRespDTO> sendCaptcha(@Valid @RequestBody SendCaptchaReqDTO request) {
        return Result.ok(authService.sendCaptcha(request));
    }
}

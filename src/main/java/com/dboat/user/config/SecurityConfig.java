package com.dboat.user.config;

import com.dboat.user.common.constants.AuthConstants;
import com.dboat.user.config.properties.AuthProperties;
import com.dboat.user.security.AuthUserDetailsService;
import com.dboat.user.security.JwtAuthenticationFilter;
import com.dboat.user.security.RestAccessDeniedHandler;
import com.dboat.user.security.RestAuthenticationEntryPoint;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Spring Security 全局安全配置
 * <p>
 * 认证模型：JWT 无状态双令牌。Access Token 由 {@link JwtAuthenticationFilter} 验签并写入
 * SecurityContext，服务端不保存会话（会话状态仅由 Refresh Token 在 Redis 中承载）。
 * </p>
 * <p>
 * 授权模型：
 * <ul>
 *   <li>路径级：白名单来自 {@code auth.security.permit-all-paths}，其余请求必须已认证</li>
 *   <li>方法级：{@link EnableMethodSecurity} 开启 {@code @PreAuthorize}，
 *       支持 {@code hasRole('ADMIN')} 与 {@code hasAuthority('iot:device:write')} 两种粒度</li>
 * </ul>
 * </p>
 * <p>
 * 跨域策略：沿用 {@code com.dboat.iot.config.WebConfig} 中注册的全局 {@code CorsFilter}，
 * 该过滤器已通过 {@code @Order(HIGHEST_PRECEDENCE)} 置于安全过滤器链之前，
 * 因此此处不再重复配置 CORS，避免响应头被写入两次导致浏览器拒绝。
 * </p>
 *
 * @author dboat
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 认证配置属性 */
    @Resource
    private AuthProperties authProperties;

    /** JWT 认证过滤器 */
    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 未认证处理器（401） */
    @Resource
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    /** 无权限处理器（403） */
    @Resource
    private RestAccessDeniedHandler restAccessDeniedHandler;

    /**
     * 安全过滤器链
     *
     * @param http HttpSecurity 构建器
     * @return 过滤器链
     * @throws Exception 构建失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> permitAllPaths = authProperties.getSecurity().getPermitAllPaths();

        http
                // ===== 无状态 JWT 认证，不需要 CSRF 令牌 =====
                .csrf(csrf -> csrf.disable())
                // ===== CORS 由全局 CorsFilter 处理，此处显式关闭避免重复写入响应头 =====
                .cors(cors -> cors.disable())
                // ===== 不创建也不使用 HttpSession =====
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ===== 关闭表单登录与 HTTP Basic，避免默认登录页与默认账号 =====
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // ===== 路径级授权 =====
                .authorizeHttpRequests(registry -> {
                    // 跨域预检请求必须放行，否则浏览器拿不到 CORS 响应头
                    registry.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // 认证入口、设备侧回调、WebSocket、接口文档
                    if (!CollectionUtils.isEmpty(permitAllPaths)) {
                        registry.requestMatchers(permitAllPaths.toArray(new String[0])).permitAll();
                    }
                    // 其余接口一律要求已认证
                    registry.anyRequest().authenticated();
                })
                // ===== 401 / 403 统一输出 Result JSON =====
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // ===== JWT 过滤器置于用户名密码认证过滤器之前 =====
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("【安全配置】JWT 无状态认证已启用，免认证白名单 {} 条：{}", permitAllPaths.size(), permitAllPaths);
        return http.build();
    }

    /**
     * 密码编码器（BCrypt，成本因子 {@value AuthConstants#BCRYPT_STRENGTH}）
     * <p>
     * BCrypt 自带随机盐且计算成本可调，成本因子 10 对应单次哈希约 100ms，
     * 在可接受的登录延迟内最大化暴力破解成本。
     * </p>
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(AuthConstants.BCRYPT_STRENGTH);
    }

    /**
     * 认证管理器
     * <p>
     * 装配 {@link DaoAuthenticationProvider}，绑定多租户账号加载器与 BCrypt 编码器。
     * 当前登录主流程由 {@code AuthService} 自行编排（需支持验证码、失败计数、多标识匹配），
     * 此 Bean 供后续扩展标准认证入口（如表单登录、单点登录）复用。
     * </p>
     *
     * @param authUserDetailsService 账号加载服务
     * @param passwordEncoder        密码编码器
     * @return 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthUserDetailsService authUserDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(authUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 保留「账号不存在」原始异常信息，便于内部日志定位；对外提示语已在 AuthCodeEnum 中统一
        provider.setHideUserNotFoundExceptions(false);
        return new ProviderManager(provider);
    }
}

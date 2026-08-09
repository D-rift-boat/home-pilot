package com.dboat.iot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Web 全局配置类 —— 跨域（CORS）配置
 * <p>
 * 注册全局 CORS 过滤器，允许前端开发服务器（如 Vite/Webpack dev server）
 * 或其他域的请求跨域访问后端 API。生产环境建议收紧 allowedOrigin 配置。
 * </p>
 *
 * @author dboat
 */
@Configuration
public class WebConfig {

    /**
     * 注册全局 CORS 过滤器
     * <p>
     * 配置策略：
     * <ul>
     *   <li>允许携带凭证（Cookie、Authorization Header）</li>
     *   <li>允许所有来源（开发阶段，生产环境应限制具体域名）</li>
     *   <li>允许所有请求头和请求方法</li>
     *   <li>预检请求缓存 3600 秒（1 小时），减少 OPTIONS 请求次数</li>
     * </ul>
     * </p>
     *
     * @return CorsFilter 跨域过滤器实例
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带 Cookie 等凭证信息
        config.setAllowCredentials(true);
        // 允许所有来源（使用 Pattern 匹配，兼容 Spring Boot 3.x）
        config.addAllowedOriginPattern("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有 HTTP 方法（GET、POST、PUT、DELETE 等）
        config.addAllowedMethod("*");
        // 预检请求缓存时间（秒），减少 OPTIONS 预检请求
        config.setMaxAge(3600L);

        // 注册 CORS 配置到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

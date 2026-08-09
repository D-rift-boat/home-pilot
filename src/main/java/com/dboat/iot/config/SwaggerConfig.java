package com.dboat.iot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置类
 * <p>
 * 基于 SpringDoc（OpenAPI 3.0 规范）配置 API 文档的元信息。
 * 启动后可通过 /swagger-ui.html 访问交互式 API 文档页面。
 * </p>
 *
 * @author dboat
 */
@Configuration
public class SwaggerConfig {

    /**
     * 创建 OpenAPI 文档配置 Bean
     * <p>
     * 定义 API 文档的标题、描述、版本、联系人等元信息，
     * 这些信息会显示在 Swagger UI 页面的头部。
     * </p>
     *
     * @return OpenAPI 文档配置实例
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT Device Management System API")
                        .description("物联网设备管理系统 —— 设备管理、传感器数据查询、指令下发、告警管理 RESTful API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("dboat")
                        )
                );
    }
}

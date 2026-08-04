package com.dboat.iot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT Device Management System API")
                        .description("IoT device management, sensor data query, and command dispatch API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("dboat")
                        )
                );
    }
}

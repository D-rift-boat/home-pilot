package com.dboat.iot.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 全局 Jackson 序列化配置
 * <p>
 * 统一管理 HTTP 请求/响应的时间类型序列化：
 * - Instant / Date / LocalDateTime → 毫秒时间戳
 * - LocalDate → yyyy-MM-dd 字符串
 * - 反序列化支持毫秒时间戳自动转换
 * </p>
 *
 * @author dboat
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // ==================== 时区 ====================
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));

            // ==================== 时间戳序列化 默认的序列化设置  不会影响 @JsonProperty / @JsonFormat / @JsonIgnore 等注解 =====================
            // Date/Instant 序列化为时间戳（而不是 ISO 字符串）
            builder.featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 输出毫秒整数，不输出秒.纳秒小数
            builder.featuresToDisable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
            // 反序列化时也按毫秒解析
            builder.featuresToDisable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
            // 未知属性不报错（前端多传字段不影响）
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // ==================== 自定义时间序列化模块 ====================
            //JavaTimeModule javaTimeModule = new JavaTimeModule();
            //// LocalDateTime ↔ 毫秒时间戳
            //javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
            //javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
            //// LocalDate ↔ yyyy-MM-dd 字符串
            //javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer());
            //javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer());
            //builder.modules(javaTimeModule);

            // ==================== 其他通用配置 ====================
            // null 字段不序列化（减少响应体积）
            builder.serializationInclusion(JsonInclude.Include.NON_NULL);
        };
    }
}

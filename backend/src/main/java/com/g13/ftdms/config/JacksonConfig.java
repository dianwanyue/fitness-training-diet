package com.g13.ftdms.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * JSON 日期时间格式配置。
 *
 * <p>为什么需要它：{@code spring.jackson.date-format} 只作用于 {@code java.util.Date}，
 * 对 {@code java.time.*} 无效。若不单独配置，LocalDateTime 会输出 ISO 格式
 * （{@code 2026-11-01T21:30:00}），与接口文档约定的 {@code yyyy-MM-dd HH:mm:ss} 不一致。</p>
 *
 * <p>统一格式后，前后端不必各自做时区/格式转换，减少联调问题。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 日期时间格式（接口文档约定）。
     *
     * <p>用 uuuu + STRICT 而不是 yyyy + 默认 SMART：后者会把 2026-02-30
     * 静默改成 2026-02-28，严格模式才能让非法日期直接报错。</p>
     */
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    /** 日期格式（同上，严格解析） */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /**
     * 注册 java.time 的序列化/反序列化器。
     *
     * @return Jackson 构建器定制器，由 Spring Boot 在创建 ObjectMapper 时应用
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DATE));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE));
        };
    }
}

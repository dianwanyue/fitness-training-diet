package com.g13.ftdms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 全局基础 Bean 配置。
 *
 * <p>目前只提供业务时钟。单独定义 {@link Clock} 的原因：
 * 项目里"今天"的含义必须固定为业务时区（Asia/Shanghai），若直接调用
 * {@code LocalDate.now()}，当服务器时区为 UTC 时会相差 8 小时，
 * 导致"补录日期越界""归属日期错位"等问题，且难以在测试中复现。
 * 统一注入 Clock 后，业务代码口径一致，单元测试也可用 {@code Clock.fixed} 固定时间。</p>
 */
@Configuration
public class AppConfig {

    /**
     * 业务时钟。
     *
     * @param properties 应用配置（含 app.timezone）
     * @return 绑定业务时区的系统时钟
     */
    @Bean
    public Clock businessClock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.getTimezone()));
    }
}

package com.g13.ftdms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级自定义配置（对应 application.yml 的 {@code app.*} 中非 JWT 部分）。
 *
 * <p>被 {@code @ConfigurationPropertiesScan} 自动发现并注册为 Bean，
 * 由 {@link AppConfig} 用于构造业务时钟。</p>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 业务时区：所有"今天/现在"的判断以此为准，默认 Asia/Shanghai */
    private String timezone = "Asia/Shanghai";

    /** 首次启动初始化配置（演示环境用） */
    private Bootstrap bootstrap = new Bootstrap();

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * 启动引导配置：是否在库中没有管理员时自动创建一个演示管理员。
     *
     * <p>默认关闭。h2 profile 中打开，便于"下载即跑"；正式部署请保持关闭，
     * 由管理员通过 SQL 提升角色（见说明文档 3.4）。</p>
     */
    public static class Bootstrap {
        /** 是否启用引导创建 */
        private boolean enabled = false;
        /** 管理员用户名 */
        private String adminUsername = "admin";
        /** 管理员初始口令（首次启动后应立即修改） */
        private String adminPassword = "Admin@12345";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }
}

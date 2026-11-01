package com.g13.ftdms.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 相关配置项（对应 application.yml 中的 {@code app.jwt.*}）。
 *
 * <p>为什么用配置类而不是散落的 @Value：密钥与有效期属于"需要按环境替换"的参数，
 * 集中绑定后可在启动时校验格式（如密钥长度），避免运行到签发令牌时才报错。</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥：HS256 要求 ≥ 32 字节；生产环境必须用环境变量 JWT_SECRET 覆盖 */
    private String secret = "ftdms-dev-only-secret-key-change-in-production-2026";

    /** 令牌有效期（秒），默认 7200（2 小时），与接口文档一致 */
    private long expireSeconds = 7200L;

    /** 签发者标识，解析时会校验，防止其它系统的令牌被误用 */
    private String issuer = "ftdms";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}

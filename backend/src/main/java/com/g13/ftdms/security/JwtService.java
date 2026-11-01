package com.g13.ftdms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析服务。
 *
 * <p>令牌载荷（claims）：sub=用户编号、username=用户名、role=角色、iss=签发者、iat/exp=签发与过期时间。
 * 不放入手机号、姓名等身份信息，满足"仅传输必要字段"的隐私要求。</p>
 *
 * <p>密钥派生：直接把配置中的 UTF-8 字节交给 HMAC-SHA 构造器；
 * 若密钥短于 32 字节，jjwt 会抛 WeakKeyException，属于启动期即可发现的配置错误。</p>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 快速失败：配置错误应在启动时暴露，而不是等到第一次登录
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节，无法用于 HS256 签名");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发访问令牌。
     *
     * @param userId   用户主键
     * @param username 用户名
     * @param role     角色（USER / ADMIN）
     * @return 紧凑格式的 JWT 字符串
     */
    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpireSeconds());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌，成功时返回身份主体。
     *
     * <p>校验内容：签名、签发者、过期时间。任一不满足即抛 {@link JwtException}。</p>
     *
     * @param token 原始令牌（不含 Bearer 前缀）
     * @return 身份主体
     * @throws JwtException 签名错误、已过期、签发者不符等
     */
    public UserPrincipal parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        return new UserPrincipal(userId, username, role);
    }

    /**
     * 读取令牌的过期时间，供登出时写入失效名单（名单只需保留到该时刻）。
     *
     * @param token 原始令牌
     * @return 过期时间戳（毫秒）；令牌已损坏时返回当前时间，表示可立即清除
     */
    public long expiryOf(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getExpiration().getTime();
        } catch (ExpiredJwtException ex) {
            return ex.getClaims().getExpiration().getTime();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("解析令牌过期时间失败：{}", ex.getMessage());
            return System.currentTimeMillis();
        }
    }

    /** 令牌有效期（秒），用于登录响应中的 expiresIn 字段 */
    public long expireSeconds() {
        return properties.getExpireSeconds();
    }
}

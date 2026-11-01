package com.g13.ftdms.config;

import com.g13.ftdms.security.JwtAuthenticationFilter;
import com.g13.ftdms.security.RestAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置：无状态 JWT 鉴权 + 接口级授权 + 口令散列策略。
 *
 * <p>三条关键决策：
 * <ol>
 *   <li><b>关闭 Session</b>（{@code STATELESS}）：手机端每次请求都带令牌，
 *       服务端不存会话，便于水平扩容；</li>
 *   <li><b>关闭 CSRF</b>：不依赖 Cookie 传递凭据，CSRF 攻击面不成立；</li>
 *   <li><b>在过滤器链中加入 JWT 过滤器</b>，并统一认证/授权失败出口。</li>
 * </ol></p>
 *
 * <p>授权规则按"先具体后宽泛"排列：公开接口 → 管理接口（要求 ADMIN）→ 其余一律要求已登录。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RestAuthEntryPoint restAuthEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthEntryPoint = restAuthEntryPoint;
    }

    /**
     * 口令编码器。
     *
     * <p>BCrypt 自带随机盐、计算成本可调，是《接口文档》约定的散列方式；
     * 注册与改密统一调用 {@code encode}，登录用 {@code matches} 比对，库里永不存明文。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10 为 BCrypt 默认强度，单次校验约 60–80 ms，兼顾安全与登录耗时
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 过滤器链定义。
     *
     * @param http Spring Security 构建器
     * @return 生效的过滤器链
     * @throws Exception 构建失败（配置错误时启动即失败）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 注册与登录必须匿名可访问；错误页放行以免掩盖真实错误码
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 管理接口单独鉴权：只有 ADMIN 角色可调用（对应接口文档 H 族）
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 其余接口一律要求已认证
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAuthEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS 配置。
     *
     * <p>开发期前端地址为 {@code http://localhost:5173}，Android 真机通过局域网 IP 访问，
     * 来源不固定，故放开来源匹配；<b>生产环境应收敛为具体域名</b>（见说明文档「配置改动说明」）。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

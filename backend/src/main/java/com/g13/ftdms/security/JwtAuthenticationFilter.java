package com.g13.ftdms.security;

import com.g13.ftdms.repository.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 *
 * <p>执行位置：Spring Security 过滤器链中、{@code UsernamePasswordAuthenticationFilter} 之前。
 * 职责单一——只做"从请求头取令牌 → 校验 → 写入 SecurityContext"，不做任何业务判断。</p>
 *
 * <p>三种情况的处理：
 * <ol>
 *   <li>没有 Authorization 头：直接放行，交给后续的授权规则决定是否需要登录（公开接口可访问）；</li>
 *   <li>令牌有效且未被登出：写入认证信息，后续 Controller 可用 {@code @AuthenticationPrincipal} 取用；</li>
 *   <li>令牌无效/过期/已登出：清空上下文后放行，最终由 {@link RestAuthenticationEntryPoint} 返回 1002。</li>
 * </ol>
 * 这样设计的好处是"认证"与"授权"分离，公开接口不会因为携带了坏令牌而报错。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final UserAccountRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, TokenBlacklist tokenBlacklist,
                                   UserAccountRepository userRepository) {
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (tokenBlacklist.isRevoked(token)) {
                // 已登出的令牌：不写入上下文，交给入口点返回 1002
                log.debug("令牌已登出，拒绝使用 path={}", request.getRequestURI());
                SecurityContextHolder.clearContext();
            } else {
                UserPrincipal claims = jwtService.parse(token);
                // JWT remains signed after an account is disabled/deleted. Check the current row
                // on every request so account management is an effective authorization boundary.
                var account = userRepository.findById(claims.id())
                        .filter(user -> "ACTIVE".equals(user.getStatus()));
                if (account.isEmpty()) {
                    SecurityContextHolder.clearContext();
                } else {
                    var user = account.get();
                    UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // 签名错误、格式错误、已过期：同样不抛出，交由后续授权规则处理
            log.debug("令牌校验失败 path={} reason={}", request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}

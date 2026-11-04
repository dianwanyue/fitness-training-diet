package com.g13.ftdms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户身份（放入 SecurityContext 的主体对象）。
 *
 * <p>只保留鉴权必需的最小信息：用户编号、用户名、角色。
 * 不携带口令散列，避免敏感数据随 SecurityContext 在请求内传递。</p>
 *
 * <p>Controller 通过 {@code @AuthenticationPrincipal UserPrincipal me} 直接取得，
 * 由此保证"用户归属"始终来自令牌而非请求参数——这是跨账号隔离的第一道防线。</p>
 *
 * @param id       用户主键
 * @param username 登录用户名
 * @param role     角色：USER / ADMIN
 */
public record UserPrincipal(Long id, String username, String role) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security 约定角色权限带 ROLE_ 前缀，hasRole("ADMIN") 才能匹配
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        // 令牌认证已完成，不需要再次比对口令
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 过滤器已检查数据库中的当前账户状态，只有 ACTIVE 账户才会创建认证主体。
        return true;
    }
}

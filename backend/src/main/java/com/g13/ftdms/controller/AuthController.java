package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.AuthDtos;
import com.g13.ftdms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户与鉴权接口（接口族 A1–A3）。
 *
 * <p>Controller 只做三件事：取参数、调 Service、包统一响应。
 * 不写业务判断，保证"接口层薄、业务层厚"，便于单测与复用。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * A1 用户注册。
     *
     * @param request 注册请求（用户名、口令及可选资料）
     * @return 新用户概要
     */
    @PostMapping("/register")
    public ApiResponse<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /**
     * A2 用户登录。
     *
     * @param request 用户名与口令
     * @return 令牌、有效期与用户概要
     */
    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * A3 退出登录（把当前令牌置为失效）。
     *
     * @param authorization 请求头，形如 {@code Bearer xxx.yyy.zzz}
     * @return 无业务数据
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        }
        authService.logout(token);
        return ApiResponse.ok();
    }
}

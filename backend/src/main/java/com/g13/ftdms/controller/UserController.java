package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.AuthDtos;
import com.g13.ftdms.dto.UserDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口（接口族 A4–A6）。
 *
 * <p>所有方法都通过 {@code @AuthenticationPrincipal} 取得当前用户，
 * 接口路径中不出现 userId——从设计上杜绝"改别人的资料"。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * A4 查询当前用户资料。
     *
     * @param me 当前登录用户（由 JWT 过滤器写入）
     * @return 资料视图
     */
    @GetMapping("/me")
    public ApiResponse<UserDtos.ProfileResponse> me(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(userService.me(me.id()));
    }

    /**
     * A5 修改当前用户资料。
     *
     * @param me      当前登录用户
     * @param request 待修改字段
     * @return 修改后的关键字段
     */
    @PutMapping("/me")
    public ApiResponse<UserDtos.UpdateProfileResponse> update(@AuthenticationPrincipal UserPrincipal me,
                                                             @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(me.id(), request));
    }

    /**
     * A6 修改登录口令。
     *
     * @param me      当前登录用户
     * @param request 原口令与新口令
     * @return 无业务数据
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal me,
                                            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        userService.changePassword(me.id(), request);
        return ApiResponse.ok();
    }
}

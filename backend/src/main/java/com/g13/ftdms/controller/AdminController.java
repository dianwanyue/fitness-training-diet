package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.AdminDtos;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理接口（接口族 H1–H5）。
 *
 * <p>整个 {@code /api/v1/admin/**} 在 SecurityConfig 中要求 ADMIN 角色，
 * 因此这里不再逐个方法标注 {@code @PreAuthorize}；如需更细粒度控制，
 * 可在此层叠加方法级注解（已开启 {@code @EnableMethodSecurity}）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * H1 新增公共动作。
     *
     * @param request 动作信息
     * @return 新增结果
     */
    @PostMapping("/exercises")
    public ApiResponse<CatalogDtos.AdminChangeResponse> createExercise(
            @Valid @RequestBody CatalogDtos.AdminExerciseCreateRequest request) {
        return ApiResponse.ok(adminService.createExercise(request));
    }

    /**
     * H2 修改或停用动作。
     *
     * @param id      动作编号
     * @param request 待修改字段
     * @return 修改后的状态
     */
    @PutMapping("/exercises/{id}")
    public ApiResponse<CatalogDtos.AdminChangeResponse> updateExercise(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.AdminExerciseUpdateRequest request) {
        return ApiResponse.ok(adminService.updateExercise(id, request));
    }

    /**
     * H3 新增公共食物。
     *
     * @param request 食物营养信息（source 必填）
     * @return 新增结果
     */
    @PostMapping("/foods")
    public ApiResponse<CatalogDtos.AdminChangeResponse> createFood(
            @Valid @RequestBody CatalogDtos.AdminFoodCreateRequest request) {
        return ApiResponse.ok(adminService.createFood(request));
    }

    /**
     * H4 修改或停用食物。
     *
     * @param id      食物编号
     * @param request 待修改字段
     * @return 修改后的状态
     */
    @PutMapping("/foods/{id}")
    public ApiResponse<CatalogDtos.AdminChangeResponse> updateFood(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.AdminFoodUpdateRequest request) {
        return ApiResponse.ok(adminService.updateFood(id, request));
    }

    /**
     * H5 变更账户状态。
     *
     * @param me      当前管理员（用于阻止停用自己）
     * @param id      目标用户编号
     * @param request 目标状态
     * @return 变更结果
     */
    @PutMapping("/users/{id}/status")
    public ApiResponse<AdminDtos.UserStatusResponse> changeUserStatus(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.UserStatusRequest request) {
        return ApiResponse.ok(adminService.changeUserStatus(me.id(), id, request.status()));
    }
}

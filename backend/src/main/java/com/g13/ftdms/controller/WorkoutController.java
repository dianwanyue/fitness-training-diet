package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.WorkoutDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.WorkoutService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 训练记录接口（接口族 C7–C9）。
 */
@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /**
     * C7 查询训练记录列表（含组明细）。
     *
     * @param me   当前用户
     * @param from 归属日期起始
     * @param to   归属日期结束
     * @return 记录列表
     */
    @GetMapping
    public ApiResponse<List<WorkoutDtos.WorkoutView>> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(workoutService.list(me.id(), from, to));
    }

    /**
     * C8 新增训练记录（含 1–50 组明细，同一事务写入）。
     *
     * @param me      当前用户
     * @param request 记录内容
     * @return 记录编号与归属日期
     */
    @PostMapping
    public ApiResponse<WorkoutDtos.WorkoutCreateResponse> create(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody WorkoutDtos.WorkoutCreateRequest request) {
        return ApiResponse.ok(workoutService.create(me.id(), request));
    }

    /**
     * C9 删除训练记录及其组明细。
     *
     * @param me 当前用户
     * @param id 记录编号
     * @return 无业务数据
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        workoutService.delete(me.id(), id);
        return ApiResponse.ok();
    }
}

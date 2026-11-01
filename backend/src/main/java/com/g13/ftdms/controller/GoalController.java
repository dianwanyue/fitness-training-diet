package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日目标接口（接口族 B1–B3）。
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * B1 查询某日生效目标。
     *
     * @param me   当前用户
     * @param date 查询日期，缺省为今天
     * @return 目标；未设置时 data 为 null
     */
    @GetMapping("/current")
    public ApiResponse<GoalDtos.GoalResponse> current(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(goalService.current(me.id(), date));
    }

    /**
     * B2 设置目标（同日覆盖）。
     *
     * @param me      当前用户
     * @param request 生效日期与四项目标
     * @return 保存后的目标
     */
    @PostMapping
    public ApiResponse<GoalDtos.GoalResponse> upsert(@AuthenticationPrincipal UserPrincipal me,
                                                    @Valid @RequestBody GoalDtos.GoalRequest request) {
        return ApiResponse.ok(goalService.upsert(me.id(), request));
    }

    /**
     * B3 查询目标历史。
     *
     * @param me   当前用户
     * @param from 起始生效日期
     * @param to   结束生效日期
     * @return 按生效日期升序的目标列表
     */
    @GetMapping("/history")
    public ApiResponse<List<GoalDtos.GoalResponse>> history(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(goalService.history(me.id(), from, to));
    }
}

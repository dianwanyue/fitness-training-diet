package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.StatsDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 统计分析接口（接口族 F1–F3）。
 *
 * <p>三个接口共用同一组区间参数，因此区间合法性统一由 Service 层的
 * {@code DateGuard.requireRange} 校验，Controller 不重复判断。</p>
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * F1 训练完成率。
     *
     * @param me   当前用户
     * @param from 起始日期
     * @param to   结束日期
     * @return 计划数、完成数、取消数、完成率与临时训练次数
     */
    @GetMapping("/workouts")
    public ApiResponse<StatsDtos.WorkoutStats> workouts(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(statsService.workoutStats(me.id(), from, to));
    }

    /**
     * F2 每日摄入趋势。
     *
     * @param me   当前用户
     * @param from 起始日期
     * @param to   结束日期
     * @return 逐日摄入、均值与有效天数
     */
    @GetMapping("/intake")
    public ApiResponse<StatsDtos.IntakeStats> intake(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(statsService.intakeStats(me.id(), from, to));
    }

    /**
     * F3 体重趋势。
     *
     * @param me   当前用户
     * @param from 起始日期
     * @param to   结束日期
     * @return 体重序列、首末值、变化量与七日平均
     */
    @GetMapping("/weight")
    public ApiResponse<StatsDtos.WeightStats> weight(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(statsService.weightStats(me.id(), from, to));
    }
}

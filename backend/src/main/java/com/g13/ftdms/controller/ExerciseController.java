package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.ExerciseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动作库接口（接口族 C1）。
 *
 * <p>分页参数在此处做范围收敛：page ≥ 1、1 ≤ size ≤ 100，
 * 避免超大 size 拖垮数据库（与接口文档 R_PAGE / R_SIZE 一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    /**
     * C1 分页查询动作库。
     *
     * @param me          当前用户（用于包含本人自定义动作）
     * @param keyword     名称关键字
     * @param muscleGroup 肌群
     * @param page        页码，默认 1
     * @param size        每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResult<CatalogDtos.ExerciseItem>> page(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(exerciseService.page(me.id(), keyword, muscleGroup, clampPage(page), clampSize(size)));
    }

    /** 页码下限 1，防止负数导致 PageRequest 抛异常 */
    private int clampPage(int page) {
        return Math.max(page, 1);
    }

    /** 每页条数收敛到 1–100 */
    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}

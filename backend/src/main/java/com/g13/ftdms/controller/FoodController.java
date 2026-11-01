package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.dto.CatalogDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.FoodService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 食物库接口（接口族 D1–D2）。
 */
@RestController
@RequestMapping("/api/v1/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    /**
     * D1 分页查询食物库。
     *
     * @param me      当前用户
     * @param keyword 名称关键字
     * @param state   生熟状态
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果（含每 100 g 能量，便于列表直接展示）
     */
    @GetMapping
    public ApiResponse<PageResult<CatalogDtos.FoodItem>> page(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.ok(foodService.page(me.id(), keyword, state, safePage, safeSize));
    }

    /**
     * D2 查询食物详情。
     *
     * @param me 当前用户
     * @param id 食物编号
     * @return 完整营养信息与来源
     */
    @GetMapping("/{id}")
    public ApiResponse<CatalogDtos.FoodDetail> detail(@AuthenticationPrincipal UserPrincipal me,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(foodService.detail(me.id(), id));
    }
}

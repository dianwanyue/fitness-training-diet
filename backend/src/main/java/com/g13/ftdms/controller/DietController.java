package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.DietDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.DietService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 饮食记录接口（接口族 D3–D7）。
 *
 * <p>路由注意：{@code /summary} 是固定路径，必须与 {@code /{id}} 区分；
 * 这里把它们放在不同的 HTTP 方法/路径组合上，避免 "summary" 被当成 id 解析。</p>
 */
@RestController
@RequestMapping("/api/v1/diets")
public class DietController {

    private final DietService dietService;

    public DietController(DietService dietService) {
        this.dietService = dietService;
    }

    /**
     * D3 查询某日饮食记录。
     *
     * @param me   当前用户
     * @param date 查询日期
     * @return 记录列表
     */
    @GetMapping
    public ApiResponse<List<DietDtos.DietItem>> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(dietService.listByDate(me.id(), date));
    }

    /**
     * D4 新增饮食记录（服务端计算营养快照）。
     *
     * @param me      当前用户
     * @param request 日期、餐次、食物与克重
     * @return 含快照的写入结果
     */
    @PostMapping
    public ApiResponse<DietDtos.DietWriteResponse> create(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody DietDtos.DietCreateRequest request) {
        return ApiResponse.ok(dietService.create(me.id(), request));
    }

    /**
     * D5 修改饮食记录（改克重时重算快照）。
     *
     * @param me      当前用户
     * @param id      记录编号
     * @param request 新的餐次/克重
     * @return 更新后的记录
     */
    @PutMapping("/{id}")
    public ApiResponse<DietDtos.DietWriteResponse> update(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable Long id,
            @Valid @RequestBody DietDtos.DietUpdateRequest request) {
        return ApiResponse.ok(dietService.update(me.id(), id, request));
    }

    /**
     * D6 删除饮食记录。
     *
     * @param me 当前用户
     * @param id 记录编号
     * @return 无业务数据
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        dietService.delete(me.id(), id);
        return ApiResponse.ok();
    }

    /**
     * D7 查询某日饮食汇总。
     *
     * @param me   当前用户
     * @param date 汇总日期
     * @return 四项合计、按餐次分组明细与当日目标
     */
    @GetMapping("/summary")
    public ApiResponse<DietDtos.DietSummary> summary(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(dietService.summary(me.id(), date));
    }
}

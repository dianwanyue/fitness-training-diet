package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.PlanDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 训练计划接口（接口族 C2–C6）。
 *
 * <p>注意路径设计：{@code /plans/{id}/status} 只接受 POST，
 * 与"修改计划内容"的 PUT 分开，避免一次请求既改内容又改状态导致语义混乱。</p>
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * C2 查询计划列表。
     *
     * @param me     当前用户
     * @param from   起始日期
     * @param to     结束日期
     * @param status 状态过滤（可为空）
     * @return 计划概要列表
     */
    @GetMapping
    public ApiResponse<List<PlanDtos.PlanSummary>> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(planService.list(me.id(), from, to, status));
    }

    /**
     * C3 查询计划详情。
     *
     * @param me 当前用户
     * @param id 计划编号
     * @return 计划头与动作明细
     */
    @GetMapping("/{id}")
    public ApiResponse<PlanDtos.PlanDetail> detail(@AuthenticationPrincipal UserPrincipal me,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(planService.detail(me.id(), id));
    }

    /**
     * C4 新建训练计划。
     *
     * @param me      当前用户
     * @param request 计划内容
     * @return 新建结果
     */
    @PostMapping
    public ApiResponse<PlanDtos.PlanCreateResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                           @Valid @RequestBody PlanDtos.PlanSaveRequest request) {
        return ApiResponse.ok(planService.create(me.id(), request));
    }

    /**
     * C5 修改训练计划（整单替换动作清单）。
     *
     * @param me      当前用户
     * @param id      计划编号
     * @param request 新的计划内容
     * @return 修改后的状态
     */
    @PutMapping("/{id}")
    public ApiResponse<PlanDtos.PlanStatusResponse> update(@AuthenticationPrincipal UserPrincipal me,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody PlanDtos.PlanSaveRequest request) {
        return ApiResponse.ok(planService.update(me.id(), id, request));
    }

    /**
     * C6 变更计划状态（完成 / 取消）。
     *
     * @param me      当前用户
     * @param id      计划编号
     * @param request 目标状态
     * @return 变更后的状态
     */
    @PostMapping("/{id}/status")
    public ApiResponse<PlanDtos.PlanStatusResponse> changeStatus(@AuthenticationPrincipal UserPrincipal me,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody PlanDtos.PlanStatusRequest request) {
        return ApiResponse.ok(planService.changeStatus(me.id(), id, request.status()));
    }
}

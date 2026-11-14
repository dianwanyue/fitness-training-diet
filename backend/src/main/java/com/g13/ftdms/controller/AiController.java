package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.common.api.PageResult;
import com.g13.ftdms.dto.AiDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 健身分析接口（接口族 G1–G7）。
 *
 * <p>所有接口均要求登录；数据归属一律取自令牌身份，不接受 userId 参数。
 * 未授权生成返回 5001，有效记录不足返回 5003，模型不可用不报错而是
 * 返回 200 + status=DEGRADED（与《接口文档》V1.5 第 10 章一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * G1 查询授权状态。
     *
     * @param me 当前用户
     * @return 授权状态、范围与时间
     */
    @GetMapping("/consent")
    public ApiResponse<AiDtos.ConsentResponse> consent(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(aiService.consent(me.id()));
    }

    /**
     * G2 授予授权。
     *
     * @param me      当前用户
     * @param request 授权范围
     * @return 授权结果（重复授权幂等）
     */
    @PostMapping("/consent")
    public ApiResponse<AiDtos.ConsentGrantResponse> grant(@AuthenticationPrincipal UserPrincipal me,
                                                          @Valid @RequestBody AiDtos.ConsentRequest request) {
        return ApiResponse.ok(aiService.grant(me.id(), request.scope()));
    }

    /**
     * G3 撤回授权。
     *
     * @param me 当前用户
     * @return 撤回结果
     */
    @DeleteMapping("/consent")
    public ApiResponse<AiDtos.ConsentRevokeResponse> revoke(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(aiService.revoke(me.id()));
    }

    /**
     * G4 生成分析报告。
     *
     * @param me      当前用户
     * @param request 分析区间（periodDays 可省略，缺省 30）
     * @return 报告与免责提示
     */
    @PostMapping("/reports")
    public ApiResponse<AiDtos.ReportResponse> generate(@AuthenticationPrincipal UserPrincipal me,
                                                       @Valid @RequestBody(required = false) AiDtos.ReportCreateRequest request) {
        Integer periodDays = request == null ? null : request.periodDays();
        return ApiResponse.ok(aiService.generate(me.id(), periodDays));
    }

    /**
     * G5 分页查询历史报告。
     *
     * @param me   当前用户
     * @param page 页码，默认 1
     * @param size 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/reports")
    public ApiResponse<PageResult<AiDtos.ReportSummary>> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(aiService.list(me.id(), clampPage(page), clampSize(size)));
    }

    /**
     * G6 查询报告详情。
     *
     * @param me 当前用户
     * @param id 报告编号
     * @return 正文与输入摘要
     */
    @GetMapping("/reports/{id}")
    public ApiResponse<AiDtos.ReportDetail> detail(@AuthenticationPrincipal UserPrincipal me,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(aiService.detail(me.id(), id));
    }

    /**
     * G7 删除报告。
     *
     * @param me 当前用户
     * @param id 报告编号
     * @return 无业务数据
     */
    @DeleteMapping("/reports/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        aiService.delete(me.id(), id);
        return ApiResponse.ok();
    }

    /** 页码下限 1 */
    private int clampPage(int page) {
        return Math.max(page, 1);
    }

    /** 每页条数收敛到 1–100 */
    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}

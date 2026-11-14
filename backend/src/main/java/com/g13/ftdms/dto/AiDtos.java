package com.g13.ftdms.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 健身分析相关载体（接口族 G1–G7）。
 *
 * <p>字段与《接口文档》V1.5 第 10 章、数据字典 DS15/DS16 一一对应。
 * 报告正文在降级时为空字符串，而不是 null——前端可据此稳定渲染「仅统计值」状态。</p>
 */
public final class AiDtos {

    private AiDtos() {
    }

    /**
     * G1 授权状态响应。
     *
     * @param granted   当前是否处于已授权状态
     * @param scope     授权范围；从未授权时返回首版默认范围
     * @param grantedAt 最近一次授权时间，可为 null
     * @param revokedAt 最近一次撤回时间，可为 null
     */
    public record ConsentResponse(boolean granted, String scope,
                                  LocalDateTime grantedAt, LocalDateTime revokedAt) {
    }

    /**
     * G2 授权请求。首版仅允许一个范围取值。
     */
    public record ConsentRequest(
            @NotBlank(message = "授权范围不能为空")
            @Pattern(regexp = "^BODY_DIET_TRAINING_30D$",
                    message = "授权范围首版仅支持 BODY_DIET_TRAINING_30D")
            String scope
    ) {
    }

    /** G2 授权响应 */
    public record ConsentGrantResponse(boolean granted, String scope, LocalDateTime grantedAt) {
    }

    /** G3 撤回响应 */
    public record ConsentRevokeResponse(boolean granted, LocalDateTime revokedAt) {
    }

    /**
     * G4 生成报告请求。
     *
     * <p>{@code periodDays} 可省略，缺省 30 天；只接受 7 或 30，
     * 非法取值由 Service 返回 1001（与 R_PERIOD 一致）。</p>
     */
    public record ReportCreateRequest(Integer periodDays) {
    }

    /**
     * G4 生成报告响应 / G6 详情响应共用的报告视图。
     *
     * @param periodDays 分析区间天数（7 或 30），由区间日期推导
     * @param status     SUCCESS / DEGRADED
     * @param content    五段式正文；降级时为空字符串
     * @param validDays  区间内有效记录天数
     * @param notice     统计口径与免责提示
     */
    public record ReportResponse(Long id, LocalDate periodStart, LocalDate periodEnd,
                                 int periodDays, String status, String content,
                                 String modelName, String modelVersion, int validDays,
                                 LocalDateTime createdAt, String notice) {
    }

    /**
     * G5 历史报告列表项。
     */
    public record ReportSummary(Long id, LocalDate periodStart, LocalDate periodEnd,
                                int periodDays, String status, int validDays,
                                LocalDateTime createdAt) {
    }

    /**
     * G6 报告详情：在报告视图之外返回生成时的输入摘要。
     *
     * @param summaryJson 结构化摘要（DS15），用于数值核对与追溯
     */
    public record ReportDetail(Long id, LocalDate periodStart, LocalDate periodEnd,
                               int periodDays, String status, String content,
                               JsonNode summaryJson, String modelName, String modelVersion,
                               int validDays, LocalDateTime createdAt, String notice) {
    }
}

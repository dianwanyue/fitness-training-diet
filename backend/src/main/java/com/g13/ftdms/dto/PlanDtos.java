package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 训练计划相关载体（接口族 C2–C6）。
 */
public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * 计划动作入参（C4/C5 共用）。
     */
    public record PlanItemRequest(
            @NotNull(message = "动作编号不能为空")
            @Min(value = 1, message = "动作编号不合法")
            Long exerciseId,

            @NotNull(message = "目标组数不能为空")
            @Min(value = 1, message = "目标组数需在 1–20 之间")
            @Max(value = 20, message = "目标组数需在 1–20 之间")
            Integer targetSets,

            @NotNull(message = "目标次数不能为空")
            @Min(value = 1, message = "目标次数需在 1–100 之间")
            @Max(value = 100, message = "目标次数需在 1–100 之间")
            Integer targetReps,

            @DecimalMin(value = "0", message = "目标重量需在 0–500 kg 之间")
            @DecimalMax(value = "500", message = "目标重量需在 0–500 kg 之间")
            @Digits(integer = 3, fraction = 2, message = "目标重量最多 2 位小数")
            BigDecimal targetWeightKg,

            @Min(value = 0, message = "排序号需在 0–99 之间")
            @Max(value = 99, message = "排序号需在 0–99 之间")
            Integer orderNo
    ) {
    }

    /**
     * C4 新建 / C5 修改计划请求。
     *
     * <p>整单替换语义：items 即"改完之后计划的完整动作清单"，长度 1–20。
     * 用 {@code @Valid} 触发对每个明细项的级联校验。</p>
     */
    public record PlanSaveRequest(
            @NotBlank(message = "计划日期不能为空")
            @Pattern(regexp = ValidationPatterns.DATE, message = "计划日期格式需为 yyyy-MM-dd")
            String plannedDate,

            @Pattern(regexp = ValidationPatterns.NAME100, message = "计划标题需为 1–100 字，首尾不得为空白")
            String title,

            @Pattern(regexp = ValidationPatterns.NOTE, message = "备注不能超过 500 字")
            String note,

            @NotEmpty(message = "计划动作不能为空")
            @Size(min = 1, max = 20, message = "计划动作数量需在 1–20 之间")
            @Valid
            List<PlanItemRequest> items
    ) {
    }

    /**
     * C6 变更计划状态请求。
     *
     * <p>只接受 COMPLETED / CANCELLED：状态单向流转，不允许回退到 PLANNED。</p>
     */
    public record PlanStatusRequest(
            @NotBlank(message = "目标状态不能为空")
            @Pattern(regexp = "^(COMPLETED|CANCELLED)$", message = "目标状态只能是 COMPLETED 或 CANCELLED")
            String status
    ) {
    }

    /** C2 计划列表项（不含动作明细） */
    public record PlanSummary(Long id, LocalDate plannedDate, String title, String status, int itemCount) {
    }

    /** C3/C4 计划动作视图 */
    public record PlanItemView(Long exerciseId, String exerciseName, Integer targetSets,
                               Integer targetReps, BigDecimal targetWeightKg, Integer orderNo) {
    }

    /** C3 计划详情 */
    public record PlanDetail(Long id, LocalDate plannedDate, String title, String status, String note,
                             List<PlanItemView> items) {
    }

    /** C4 新建计划响应 */
    public record PlanCreateResponse(Long id, LocalDate plannedDate, String title, String status) {
    }

    /** C5/C6 状态变更响应 */
    public record PlanStatusResponse(Long id, String status, LocalDateTime updatedAt) {
    }
}

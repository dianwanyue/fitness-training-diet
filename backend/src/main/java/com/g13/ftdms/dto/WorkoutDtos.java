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
 * 训练记录相关载体（接口族 C7–C9）。
 */
public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    /**
     * 训练组入参。
     *
     * <p>{@code setNo} 可空，缺省按数组顺序自动编号；{@code weightKg} 可空，
     * 表示自重动作（如引体向上、俯卧撑）。</p>
     */
    public record WorkoutSetRequest(
            @NotNull(message = "动作编号不能为空")
            @Min(value = 1, message = "动作编号不合法")
            Long exerciseId,

            @Min(value = 1, message = "组序号需在 1–20 之间")
            @Max(value = 20, message = "组序号需在 1–20 之间")
            Integer setNo,

            @DecimalMin(value = "0", message = "重量需在 0–500 kg 之间")
            @DecimalMax(value = "500", message = "重量需在 0–500 kg 之间")
            @Digits(integer = 3, fraction = 2, message = "重量最多 2 位小数")
            BigDecimal weightKg,

            @NotNull(message = "次数不能为空")
            @Min(value = 1, message = "次数需在 1–100 之间")
            @Max(value = 100, message = "次数需在 1–100 之间")
            Integer reps
    ) {
    }

    /**
     * C8 新增训练记录请求。
     *
     * <p>{@code planId} 可空——不传即"临时训练"，这是允许用户不按计划也要记录的关键。</p>
     */
    public record WorkoutCreateRequest(
            @Min(value = 1, message = "计划编号不合法")
            Long planId,

            @NotBlank(message = "训练时间不能为空")
            @Pattern(regexp = ValidationPatterns.DATETIME, message = "训练时间格式需为 yyyy-MM-dd HH:mm:ss")
            String performedAt,

            @Pattern(regexp = ValidationPatterns.NOTE, message = "备注不能超过 500 字")
            String note,

            @NotEmpty(message = "训练组不能为空")
            @Size(min = 1, max = 50, message = "训练组数量需在 1–50 之间")
            @Valid
            List<WorkoutSetRequest> sets
    ) {
    }

    /** 训练组视图 */
    public record WorkoutSetView(Long exerciseId, String exerciseName, Integer setNo,
                                 BigDecimal weightKg, Integer reps) {
    }

    /** C7 训练记录视图（含组明细） */
    public record WorkoutView(Long id, Long planId, LocalDateTime performedAt, LocalDate logDate,
                              String note, List<WorkoutSetView> sets) {
    }

    /** C8 新增记录响应 */
    public record WorkoutCreateResponse(Long id, LocalDate logDate, Long planId) {
    }
}

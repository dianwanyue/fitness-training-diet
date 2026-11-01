package com.g13.ftdms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 饮食记录相关载体（接口族 D3–D7）。
 */
public final class DietDtos {

    private DietDtos() {
    }

    /**
     * D4 新增饮食记录请求。
     *
     * <p>克重 1–2000 g：下限排除 0 与负数，上限防止误输入（如 15000 当成 1500）。
     * 营养快照由服务端按食物库计算，客户端不需要也不允许传。</p>
     */
    public record DietCreateRequest(
            @NotBlank(message = "记录日期不能为空")
            @Pattern(regexp = ValidationPatterns.DATE, message = "记录日期格式需为 yyyy-MM-dd")
            String recordDate,

            @NotBlank(message = "餐次不能为空")
            @Pattern(regexp = ValidationPatterns.MEAL, message = "餐次取值需为 BREAKFAST / LUNCH / DINNER / SNACK")
            String meal,

            @NotNull(message = "食物编号不能为空")
            @jakarta.validation.constraints.Min(value = 1, message = "食物编号不合法")
            Long foodId,

            @NotNull(message = "摄入克重不能为空")
            @DecimalMin(value = "1", message = "摄入克重需在 1–2000 g 之间")
            @DecimalMax(value = "2000", message = "摄入克重需在 1–2000 g 之间")
            @Digits(integer = 4, fraction = 2, message = "摄入克重最多 2 位小数")
            BigDecimal grams
    ) {
    }

    /**
     * D5 修改饮食记录请求。
     *
     * <p>只允许改餐次与克重；换食物等价于"删旧建新"，避免快照与食物来源不一致。</p>
     */
    public record DietUpdateRequest(
            @Pattern(regexp = ValidationPatterns.MEAL, message = "餐次取值需为 BREAKFAST / LUNCH / DINNER / SNACK")
            String meal,

            @DecimalMin(value = "1", message = "摄入克重需在 1–2000 g 之间")
            @DecimalMax(value = "2000", message = "摄入克重需在 1–2000 g 之间")
            @Digits(integer = 4, fraction = 2, message = "摄入克重最多 2 位小数")
            BigDecimal grams
    ) {
    }

    /** D3 饮食记录项（含营养快照） */
    public record DietItem(Long id, String meal, Long foodId, String foodName, BigDecimal grams,
                           BigDecimal snapshotKcal, BigDecimal snapshotProteinG,
                           BigDecimal snapshotCarbG, BigDecimal snapshotFatG) {
    }

    /** D4/D5 写入结果 */
    public record DietWriteResponse(Long id, LocalDate recordDate, String meal, BigDecimal grams,
                                    BigDecimal snapshotKcal, BigDecimal snapshotProteinG,
                                    BigDecimal snapshotCarbG, BigDecimal snapshotFatG) {
    }

    /**
     * D7 当日饮食汇总。
     *
     * <p>{@code target} 允许为 null（当天没有生效目标），此时前端只显示摄入量、不显示进度环，
     * 这与"缺失不补零"的统计口径一致。</p>
     */
    public record DietSummary(
            LocalDate date,
            BigDecimal kcal,
            BigDecimal proteinG,
            BigDecimal carbG,
            BigDecimal fatG,
            @JsonInclude(JsonInclude.Include.ALWAYS) GoalDtos.GoalResponse target,
            Map<String, List<DietItem>> meals
    ) {
    }
}

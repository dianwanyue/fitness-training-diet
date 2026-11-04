package com.g13.ftdms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计分析相关载体（接口族 F1–F3）。
 *
 * <p>统一原则：**缺失即为 null，不补零**。例如某天没有饮食记录，
 * {@code days[].kcal} 为 null 且 {@code recorded=false}，而不是 0——
 * 否则"没记录"与"吃了 0 千卡"无法区分，均值也会被拉低。</p>
 */
public final class StatsDtos {

    private StatsDtos() {
    }

    /**
     * F1 训练完成率。
     *
     * @param plannedCount      区间内计划总数
     * @param completedCount    已完成计划数
     * @param cancelledCount    已取消计划数
     * @param completionRate    完成率 0–1；无计划时为 null（前端显示"暂无计划"）
     * @param extraWorkoutCount 未关联计划的临时训练次数
     */
    public record WorkoutStats(
            int plannedCount,
            int completedCount,
            int cancelledCount,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal completionRate,
            int extraWorkoutCount
    ) {
    }

    /** 每日摄入点 */
    public record IntakeDay(LocalDate date,
                            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal kcal,
                            boolean recorded) {
    }

    /** 摄入均值（只对有效记录日求平均） */
    public record IntakeAverage(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG) {
    }

    /**
     * F2 每日摄入趋势。
     *
     * @param days      区间内每一天（含无记录日，recorded=false）
     * @param avg       有效日均值；无任何记录时为 null
     * @param validDays 有效记录天数
     * @param target    区间末生效目标，可为 null
     */
    public record IntakeStats(
            List<IntakeDay> days,
            @JsonInclude(JsonInclude.Include.ALWAYS) IntakeAverage avg,
            int validDays,
            @JsonInclude(JsonInclude.Include.ALWAYS) GoalDtos.GoalResponse target
    ) {
    }

    /** 体重序列点 */
    public record WeightPoint(LocalDate date, BigDecimal weightKg) {
    }

    /**
     * F3 体重趋势。
     *
     * @param points      区间内有效记录点
     * @param first       首个体重，可为 null
     * @param last        末个体重，可为 null
     * @param change      last − first，可为 null
     * @param sevenDayAvg 最近 7 天平均体重，可为 null
     * @param validDays   最近 7 天中的有效记录天数
     */
    public record WeightStats(
            List<WeightPoint> points,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal first,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal last,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal change,
            @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal sevenDayAvg,
            int validDays
    ) {
    }
}

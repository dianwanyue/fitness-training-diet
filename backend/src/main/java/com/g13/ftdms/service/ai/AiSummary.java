package com.g13.ftdms.service.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AI 分析输入摘要（对应数据字典 DS15）。
 *
 * <p>该对象先由后端按确定规则算出，再序列化为 JSON 交给模型。
 * 模型只负责组织语言，不参与任何算数；因此摘要中每个数值都必须来自
 * {@code StatsService} / 档案查询，而不是模型输出。</p>
 *
 * @param periodDays  分析区间天数（7 或 30）
 * @param periodStart 区间起始日
 * @param periodEnd   区间结束日
 * @param validDays   区间内至少有一条记录的有效天数
 * @param profile     基础档案（不含姓名、手机号、用户名）
 * @param goal        区间末生效目标，可为 null
 * @param training    训练统计
 * @param intake      营养统计
 * @param weight      体重统计
 * @param notices     口径说明
 */
public record AiSummary(
        int periodDays,
        LocalDate periodStart,
        LocalDate periodEnd,
        int validDays,
        Profile profile,
        Goal goal,
        Training training,
        Intake intake,
        Weight weight,
        List<String> notices
) {

    /** 基础档案：仅发送分析所需字段，身份信息不进入摘要 */
    public record Profile(String gender, Integer age, BigDecimal heightCm, BigDecimal weightKg) {
    }

    /** 区间末生效的每日目标 */
    public record Goal(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG) {
    }

    /** 训练统计：完成率分母剔除已取消计划，实际训练次数单独计数 */
    public record Training(int plannedCount, int completedCount, int cancelledCount,
                           BigDecimal completionRate, int workoutCount, int extraWorkoutCount) {
    }

    /** 营养统计：均值只对有效记录日计算，缺失日不补零 */
    public record Intake(int validDays, BigDecimal avgKcal, BigDecimal avgProteinG,
                         BigDecimal avgCarbG, BigDecimal avgFatG) {
    }

    /** 体重统计：首末值取自区间内真实记录点，七日平均按有效日计算 */
    public record Weight(int validDays, BigDecimal first, BigDecimal last,
                         BigDecimal change, BigDecimal sevenDayAvg) {
    }
}

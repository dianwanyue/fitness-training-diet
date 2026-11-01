package com.g13.ftdms.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 营养换算器（纯函数，无状态，便于单元测试）。
 *
 * <p>公式（《项目介绍》5.2 与接口文档 7.4）：</p>
 * <pre>
 *   单项摄入量 = 每 100 g 对应营养值 × 实际摄入克重 ÷ 100
 * </pre>
 *
 * <p>精度策略：先按完整精度计算，最后统一保留 2 位小数（四舍五入），
 * 避免"先截断再相乘"造成的累计误差。示例：120 kcal/100g × 150 g ÷ 100 = 180.00 kcal。</p>
 */
public final class NutritionCalculator {

    /** 展示与入库统一保留的小数位 */
    private static final int SCALE = 2;

    private NutritionCalculator() {
    }

    /**
     * 计算某一项营养的摄入量。
     *
     * @param per100g 每 100 g 的营养值（能量 kcal 或某营养素 g）
     * @param grams   实际摄入克重
     * @return 保留 2 位小数的结果；任一入参为 null 时返回 0
     */
    public static BigDecimal amount(BigDecimal per100g, BigDecimal grams) {
        if (per100g == null || grams == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return per100g.multiply(grams)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 求和并保留 2 位小数（用于当日汇总）。
     *
     * @param values 待求和的值，可含 null
     * @return 合计值，最小为 0
     */
    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(v);
            }
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算均值（仅对有效记录日调用）。
     *
     * @param total      合计值
     * @param validCount 有效天数
     * @return 均值；有效天数为 0 时返回 null（不补零，前端显示"—"）
     */
    public static BigDecimal average(BigDecimal total, int validCount) {
        if (validCount <= 0 || total == null) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(validCount), SCALE, RoundingMode.HALF_UP);
    }
}

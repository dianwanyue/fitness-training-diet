package com.g13.ftdms.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 营养换算器单元测试。
 *
 * <p>用接口文档 7.4 的示例数据作为断言基准：
 * 每 100 g 含 120 kcal、8 g 蛋白质的食物，录入 150 g 后应为 180 kcal、12 g。</p>
 */
class NutritionCalculatorTest {

    @Test
    @DisplayName("单项换算：120 kcal/100g × 150 g = 180 kcal")
    void amountMatchesInterfaceExample() {
        assertEquals(new BigDecimal("180.00"),
                NutritionCalculator.amount(new BigDecimal("120"), new BigDecimal("150")));
        assertEquals(new BigDecimal("12.00"),
                NutritionCalculator.amount(new BigDecimal("8"), new BigDecimal("150")));
    }

    @Test
    @DisplayName("换算保留 2 位小数并四舍五入")
    void amountRoundsHalfUp() {
        // 133 ÷ 100 × 150 = 199.5，保留两位后 199.50
        assertEquals(new BigDecimal("199.50"),
                NutritionCalculator.amount(new BigDecimal("133"), new BigDecimal("150")));
        // 1.005 × 1 ÷ 100 需要四舍五入到 0.01
        assertEquals(new BigDecimal("0.01"),
                NutritionCalculator.amount(new BigDecimal("1.005"), new BigDecimal("1")));
    }

    @Test
    @DisplayName("入参为空时返回 0，不抛异常")
    void amountHandlesNull() {
        assertEquals(new BigDecimal("0.00"), NutritionCalculator.amount(null, new BigDecimal("100")));
        assertEquals(new BigDecimal("0.00"), NutritionCalculator.amount(new BigDecimal("100"), null));
    }

    @Test
    @DisplayName("无有效记录日时均值为 null，而不是 0")
    void averageReturnsNullWhenNoValidDay() {
        assertNull(NutritionCalculator.average(new BigDecimal("0"), 0));
        assertEquals(new BigDecimal("100.00"),
                NutritionCalculator.average(new BigDecimal("300"), 3));
    }

    @Test
    @DisplayName("求和忽略 null 并保留 2 位小数")
    void sumIgnoresNull() {
        assertEquals(new BigDecimal("300.00"), NutritionCalculator.sum(
                new BigDecimal("100"), null, new BigDecimal("200")));
    }
}

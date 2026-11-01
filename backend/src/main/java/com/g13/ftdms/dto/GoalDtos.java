package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日目标相关载体（接口族 B）。
 */
public final class GoalDtos {

    private GoalDtos() {
    }

    /**
     * B2 设置目标请求。
     *
     * <p>数值区间对应数据字典：能量 500–6000 kcal，三大营养素 0–1000 g。
     * 格式（最多 2 位小数）由 {@link Digits} 保证，避免 121.333 这类值入库后四舍五入引发争议。</p>
     */
    public record GoalRequest(
            @NotBlank(message = "生效日期不能为空")
            @Pattern(regexp = ValidationPatterns.DATE, message = "生效日期格式需为 yyyy-MM-dd")
            String effectiveDate,

            @NotNull(message = "能量目标不能为空")
            @DecimalMin(value = "500", message = "能量目标需在 500–6000 kcal 之间")
            @DecimalMax(value = "6000", message = "能量目标需在 500–6000 kcal 之间")
            @Digits(integer = 4, fraction = 2, message = "能量目标最多 2 位小数")
            BigDecimal kcal,

            @DecimalMin(value = "0", message = "蛋白质目标需在 0–1000 g 之间")
            @DecimalMax(value = "1000", message = "蛋白质目标需在 0–1000 g 之间")
            @Digits(integer = 4, fraction = 2, message = "蛋白质目标最多 2 位小数")
            BigDecimal proteinG,

            @DecimalMin(value = "0", message = "碳水目标需在 0–1000 g 之间")
            @DecimalMax(value = "1000", message = "碳水目标需在 0–1000 g 之间")
            @Digits(integer = 4, fraction = 2, message = "碳水目标最多 2 位小数")
            BigDecimal carbG,

            @DecimalMin(value = "0", message = "脂肪目标需在 0–1000 g 之间")
            @DecimalMax(value = "1000", message = "脂肪目标需在 0–1000 g 之间")
            @Digits(integer = 4, fraction = 2, message = "脂肪目标最多 2 位小数")
            BigDecimal fatG
    ) {
    }

    /**
     * B1/B2 目标响应。
     */
    public record GoalResponse(
            Long id,
            LocalDate effectiveDate,
            BigDecimal kcal,
            BigDecimal proteinG,
            BigDecimal carbG,
            BigDecimal fatG
    ) {
    }
}

package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 基础数据（动作库 / 食物库）相关载体（接口族 C1、D1、D2 与 H1–H4）。
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** C1 动作列表项 */
    public record ExerciseItem(Long id, String name, String muscleGroup, String equipment, Integer isPublic) {
    }

    /** D1 食物列表项（列表只带能量，减少无用字段传输） */
    public record FoodItem(Long id, String name, BigDecimal kcalPer100g, String state, Integer isPublic) {
    }

    /** D2 食物详情 */
    public record FoodDetail(Long id, String name, String brand, BigDecimal kcalPer100g,
                             BigDecimal proteinPer100g, BigDecimal carbPer100g, BigDecimal fatPer100g,
                             String state, String source) {
    }

    /**
     * H1 新增公共动作请求。
     */
    public record AdminExerciseCreateRequest(
            @NotBlank(message = "动作名称不能为空")
            @Pattern(regexp = ValidationPatterns.NAME100, message = "动作名称需为 1–100 字，首尾不得为空白")
            String name,

            @Pattern(regexp = ValidationPatterns.TEXT50, message = "主要肌群需为 1–50 字")
            String muscleGroup,

            @Pattern(regexp = ValidationPatterns.TEXT50, message = "器械类型需为 1–50 字")
            String equipment
    ) {
    }

    /**
     * H2 修改/停用动作请求（字段缺省表示不修改）。
     */
    public record AdminExerciseUpdateRequest(
            @Pattern(regexp = ValidationPatterns.NAME100, message = "动作名称需为 1–100 字，首尾不得为空白")
            String name,

            @Pattern(regexp = ValidationPatterns.TEXT50, message = "主要肌群需为 1–50 字")
            String muscleGroup,

            @Pattern(regexp = ValidationPatterns.TEXT50, message = "器械类型需为 1–50 字")
            String equipment,

            @Pattern(regexp = ValidationPatterns.ACC_STATUS, message = "状态取值需为 ACTIVE 或 DISABLED")
            String status
    ) {
    }

    /**
     * H3 新增公共食物请求。
     *
     * <p>{@code source} 必填：营养数据必须可追溯（对应数据字典序号 54 与接口文档 11.3）。
     * 每 100 g 营养值上限：能量 900 kcal、三大营养素 100 g。</p>
     */
    public record AdminFoodCreateRequest(
            @NotBlank(message = "食物名称不能为空")
            @Pattern(regexp = ValidationPatterns.NAME100, message = "食物名称需为 1–100 字，首尾不得为空白")
            String name,

            @Pattern(regexp = ValidationPatterns.TEXT100, message = "品牌或规格需为 1–100 字")
            String brand,

            @DecimalMin(value = "0", message = "每 100 g 能量需在 0–900 kcal 之间")
            @DecimalMax(value = "900", message = "每 100 g 能量需在 0–900 kcal 之间")
            @Digits(integer = 3, fraction = 2, message = "能量最多 2 位小数")
            BigDecimal kcalPer100g,

            @DecimalMin(value = "0", message = "每 100 g 蛋白质需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 蛋白质需在 0–100 g 之间")
            @Digits(integer = 3, fraction = 2, message = "蛋白质最多 2 位小数")
            BigDecimal proteinPer100g,

            @DecimalMin(value = "0", message = "每 100 g 碳水需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 碳水需在 0–100 g 之间")
            @Digits(integer = 3, fraction = 2, message = "碳水最多 2 位小数")
            BigDecimal carbPer100g,

            @DecimalMin(value = "0", message = "每 100 g 脂肪需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 脂肪需在 0–100 g 之间")
            @Digits(integer = 3, fraction = 2, message = "脂肪最多 2 位小数")
            BigDecimal fatPer100g,

            @NotBlank(message = "生熟状态不能为空")
            @Pattern(regexp = ValidationPatterns.FOOD_STATE, message = "生熟状态取值需为 RAW / COOKED / BRAND")
            String state,

            @NotBlank(message = "营养数据来源不能为空")
            @Pattern(regexp = ValidationPatterns.TEXT255, message = "营养数据来源需为 1–255 字")
            String source
    ) {
    }

    /**
     * H4 修改/停用食物请求（字段缺省表示不修改）。
     */
    public record AdminFoodUpdateRequest(
            @DecimalMin(value = "0", message = "每 100 g 能量需在 0–900 kcal 之间")
            @DecimalMax(value = "900", message = "每 100 g 能量需在 0–900 kcal 之间")
            BigDecimal kcalPer100g,

            @DecimalMin(value = "0", message = "每 100 g 蛋白质需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 蛋白质需在 0–100 g 之间")
            BigDecimal proteinPer100g,

            @DecimalMin(value = "0", message = "每 100 g 碳水需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 碳水需在 0–100 g 之间")
            BigDecimal carbPer100g,

            @DecimalMin(value = "0", message = "每 100 g 脂肪需在 0–100 g 之间")
            @DecimalMax(value = "100", message = "每 100 g 脂肪需在 0–100 g 之间")
            BigDecimal fatPer100g,

            @Pattern(regexp = ValidationPatterns.TEXT255, message = "营养数据来源需为 1–255 字")
            String source,

            @Pattern(regexp = ValidationPatterns.ACC_STATUS, message = "状态取值需为 ACTIVE 或 DISABLED")
            String status
    ) {
    }

    /** 管理端变更结果（H1–H4 通用，返回主键与最新状态） */
    public record AdminChangeResponse(Long id, String name, String status) {
    }
}

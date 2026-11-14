package com.g13.ftdms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出与统计摘要的核对器。
 *
 * <p>SRS 要求「模型输出需与原始摘要核对，不一致时不作为确定结论展示」。
 * 这里做轻量、可解释的两项检查：</p>
 * <ol>
 *   <li>五段式标题是否齐全；</li>
 *   <li>关键统计值（有效天数、计划数、完成数、实际训练次数、日均摄入、体重变化）
 *       是否在正文中出现。</li>
 * </ol>
 *
 * <p>核对失败不改写模型原文，也不把报告误标为模型不可用；由调用方在
 * {@code notice} 中提示「请以统计值为准」，避免把模型措辞当成确定结论。</p>
 */
public final class AiContentGuard {

    /** 约定的五段标题 */
    public static final List<String> SECTIONS = List.of(
            "数据概况", "训练执行", "营养摄入", "体重趋势", "可采取的调整");

    private AiContentGuard() {
    }

    /**
     * 生成阶段核对：使用内存中的摘要对象。
     *
     * @param content 模型正文
     * @param summary 输入摘要
     * @return 核对结果
     */
    public static Review review(String content, AiSummary summary) {
        String text = normalize(content);
        List<String> missingSections = missingSections(text);
        List<String> missingValues = new ArrayList<>();
        check(text, String.valueOf(summary.validDays()), "有效天数", missingValues);
        check(text, String.valueOf(summary.training().plannedCount()), "计划数", missingValues);
        check(text, String.valueOf(summary.training().completedCount()), "完成数", missingValues);
        check(text, String.valueOf(summary.training().workoutCount()), "实际训练次数", missingValues);
        if (summary.intake().avgKcal() != null) {
            check(text, integerText(summary.intake().avgKcal()), "日均摄入", missingValues);
        }
        if (summary.weight().change() != null) {
            check(text, decimalText(summary.weight().change().abs()), "体重变化", missingValues);
        }
        return result(missingSections, missingValues);
    }

    /**
     * 读取阶段核对：摘要已落库为 JSON，用于 G5/G6 重新计算 notice，
     * 保证详情页与生成时的提示口径一致。
     *
     * @param content 报告正文
     * @param summary 摘要 JSON（DS15）
     * @return 核对结果
     */
    public static Review review(String content, JsonNode summary) {
        String text = normalize(content);
        List<String> missingSections = missingSections(text);
        List<String> missingValues = new ArrayList<>();
        check(text, summary.path("validDays").asText(""), "有效天数", missingValues);
        JsonNode training = summary.path("training");
        check(text, training.path("plannedCount").asText(""), "计划数", missingValues);
        check(text, training.path("completedCount").asText(""), "完成数", missingValues);
        check(text, training.path("workoutCount").asText(""), "实际训练次数", missingValues);
        JsonNode intake = summary.path("intake");
        if (intake.hasNonNull("avgKcal")) {
            check(text, integerText(intake.get("avgKcal").decimalValue()), "日均摄入", missingValues);
        }
        JsonNode weight = summary.path("weight");
        if (weight.hasNonNull("change")) {
            check(text, decimalText(weight.get("change").decimalValue().abs()), "体重变化", missingValues);
        }
        return result(missingSections, missingValues);
    }

    private static String normalize(String content) {
        return content == null ? "" : content.replace(",", "").replace("，", "");
    }

    private static List<String> missingSections(String text) {
        List<String> missing = new ArrayList<>();
        for (String section : SECTIONS) {
            if (!text.contains(section)) {
                missing.add(section);
            }
        }
        return missing;
    }

    private static Review result(List<String> missingSections, List<String> missingValues) {
        boolean consistent = missingSections.isEmpty() && missingValues.isEmpty();
        return new Review(consistent, missingSections, missingValues);
    }

    /** 数字以整串或整数近似形式出现即视为命中；零值不强制出现（模型常用「没有」「暂无」表达） */
    private static void check(String text, String value, String label, List<String> missing) {
        if (value == null || value.isBlank() || "0".equals(value)) {
            return;
        }
        if (!text.contains(value)) {
            missing.add(label + "=" + value);
        }
    }

    /** 1234.00 → "1234" */
    private static String integerText(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** 1.20 → "1.2"，1.00 → "1" */
    private static String decimalText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 核对结果。
     *
     * @param consistent      是否完全对应
     * @param missingSections 缺失的段落标题
     * @param missingValues   未在正文出现的关键数值
     */
    public record Review(boolean consistent, List<String> missingSections, List<String> missingValues) {
    }
}

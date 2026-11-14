package com.g13.ftdms.service.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * 输出核对测试：零值不强制出现，关键非零值与五段标题必须齐全。
 */
class AiContentGuardTest {

    private static final AiSummary SUMMARY = new AiSummary(
            7, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14), 3,
            new AiSummary.Profile("MALE", 28, new BigDecimal("175.00"), new BigDecimal("70.00")),
            null,
            new AiSummary.Training(0, 0, 0, null, 0, 0),
            new AiSummary.Intake(0, null, null, null, null),
            new AiSummary.Weight(3, new BigDecimal("69.60"), new BigDecimal("70.00"),
                    new BigDecimal("0.40"), new BigDecimal("69.80")),
            List.of("缺失日不计入均值"));

    @Test
    void acceptsCompleteSectionsAndKeyValues() {
        String content = "【数据概况】有效记录 3 天。\n【训练执行】暂无计划。\n"
                + "【营养摄入】暂无记录。\n【体重趋势】变化 0.4 kg。\n【可采取的调整】保持记录。";
        assertTrue(AiContentGuard.review(content, SUMMARY).consistent());
    }

    @Test
    void flagsMissingSectionOrValue() {
        String content = "【数据概况】有效记录 3 天。\n【训练执行】暂无计划。\n【营养摄入】暂无记录。\n【可采取的调整】保持记录。";
        AiContentGuard.Review review = AiContentGuard.review(content, SUMMARY);
        assertFalse(review.consistent());
        assertTrue(review.missingSections().contains("体重趋势"));
        assertTrue(review.missingValues().stream().anyMatch(v -> v.contains("体重变化")));
    }

    @Test
    void reviewsPersistedSummaryJsonForDetailNotice() throws Exception {
        String json = """
                {"validDays":3,"training":{"plannedCount":0,"completedCount":0,"workoutCount":0},
                 "intake":{"validDays":0},"weight":{"change":0.40}}
                """;
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        String content = "【数据概况】有效记录 3 天。\n【训练执行】暂无计划。\n"
                + "【营养摄入】暂无记录。\n【体重趋势】变化 0.4 kg。\n【可采取的调整】保持记录。";
        assertTrue(AiContentGuard.review(content, node).consistent());

        AiContentGuard.Review missing = AiContentGuard.review(content.replace("变化 0.4 kg", "趋势平稳"), node);
        assertFalse(missing.consistent());
        assertTrue(missing.missingValues().stream().anyMatch(v -> v.contains("体重变化")));
    }
}

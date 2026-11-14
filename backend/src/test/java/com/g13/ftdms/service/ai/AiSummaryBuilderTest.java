package com.g13.ftdms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.g13.ftdms.config.AiProperties;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.dto.StatsDtos;
import com.g13.ftdms.dto.UserDtos;
import com.g13.ftdms.entity.WorkoutLog;
import com.g13.ftdms.repository.WorkoutLogRepository;
import com.g13.ftdms.service.StatsService;
import com.g13.ftdms.service.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 摘要构建器单元测试：验证有效天数按三类记录去重、目标缺失为 null、
 * 以及 8 KB 上限保护。
 */
class AiSummaryBuilderTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate FROM = LocalDate.of(2026, 11, 8);
    private static final LocalDate TO = LocalDate.of(2026, 11, 14);

    @Test
    void buildsUnionValidDaysAndKeepsMissingValuesNull() {
        StatsService stats = mock(StatsService.class);
        UserService users = mock(UserService.class);
        WorkoutLogRepository workouts = mock(WorkoutLogRepository.class);
        AiProperties properties = new AiProperties();
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        when(stats.workoutStats(USER_ID, FROM, TO))
                .thenReturn(new StatsDtos.WorkoutStats(5, 3, 1, new BigDecimal("0.7500"), 2));
        when(stats.intakeStats(USER_ID, FROM, TO)).thenReturn(new StatsDtos.IntakeStats(
                List.of(
                        new StatsDtos.IntakeDay(FROM, new BigDecimal("2100.00"), true),
                        new StatsDtos.IntakeDay(FROM.plusDays(1), null, false),
                        new StatsDtos.IntakeDay(FROM.plusDays(2), new BigDecimal("2200.00"), true),
                        new StatsDtos.IntakeDay(FROM.plusDays(3), null, false),
                        new StatsDtos.IntakeDay(FROM.plusDays(4), new BigDecimal("2000.00"), true),
                        new StatsDtos.IntakeDay(FROM.plusDays(5), new BigDecimal("2300.00"), true),
                        new StatsDtos.IntakeDay(FROM.plusDays(6), null, false)),
                new StatsDtos.IntakeAverage(new BigDecimal("2150.00"), new BigDecimal("120.00"),
                        new BigDecimal("240.00"), new BigDecimal("70.00")),
                4,
                null));
        when(stats.weightStats(USER_ID, FROM, TO)).thenReturn(new StatsDtos.WeightStats(
                List.of(new StatsDtos.WeightPoint(FROM, new BigDecimal("70.00")),
                        new StatsDtos.WeightPoint(TO, new BigDecimal("69.50"))),
                new BigDecimal("70.00"), new BigDecimal("69.50"), new BigDecimal("-0.50"),
                new BigDecimal("69.75"), 1));
        when(users.me(USER_ID)).thenReturn(new UserDtos.ProfileResponse(
                USER_ID, "lifter01", "阿锦", "MALE", LocalDate.of(1998, 1, 1),
                new BigDecimal("175.00"), "USER", "ACTIVE", LocalDateTime.of(2026, 10, 1, 9, 0)));

        WorkoutLog log = new WorkoutLog();
        log.setLogDate(FROM.plusDays(1));
        when(workouts.findByUserIdAndLogDateBetweenOrderByPerformedAtDesc(USER_ID, FROM, TO))
                .thenReturn(List.of(log));

        AiSummaryBuilder builder = new AiSummaryBuilder(stats, users, workouts, mapper, properties);
        AiSummary summary = builder.build(USER_ID, 7, FROM, TO);

        // 饮食 4 天 + 训练 1 天 + 体重 2 天，其中 11-08 重合 → 6 天
        assertEquals(6, summary.validDays());
        assertEquals(1, summary.training().workoutCount());
        assertEquals(4, summary.intake().validDays());
        assertEquals(new BigDecimal("2150.00"), summary.intake().avgKcal());
        assertEquals(new BigDecimal("-0.50"), summary.weight().change());
        // 区间末没有目标，必须保持 null，不能补零
        assertNull(summary.goal());
        assertEquals(28, summary.profile().age());

        String json = builder.toJson(summary);
        assertTrue(json.contains("\"validDays\":6"));
        assertTrue(json.contains("\"plannedCount\":5"));
        assertTrue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 8192);
    }

    @Test
    void rejectsSummaryLargerThanConfiguredLimit() {
        StatsService stats = mock(StatsService.class);
        UserService users = mock(UserService.class);
        WorkoutLogRepository workouts = mock(WorkoutLogRepository.class);
        AiProperties properties = new AiProperties();
        properties.setMaxSummaryBytes(10);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        when(stats.workoutStats(USER_ID, FROM, TO))
                .thenReturn(new StatsDtos.WorkoutStats(0, 0, 0, null, 0));
        when(stats.intakeStats(USER_ID, FROM, TO)).thenReturn(new StatsDtos.IntakeStats(
                List.of(), null, 0, null));
        when(stats.weightStats(USER_ID, FROM, TO)).thenReturn(new StatsDtos.WeightStats(
                List.of(), null, null, null, null, 0));
        when(users.me(USER_ID)).thenReturn(new UserDtos.ProfileResponse(
                USER_ID, "u", null, "UNKNOWN", null, null, "USER", "ACTIVE", LocalDateTime.now()));

        AiSummaryBuilder builder = new AiSummaryBuilder(stats, users, workouts, mapper, properties);
        AiSummary summary = builder.build(USER_ID, 7, FROM, TO);
        assertThrows(IllegalStateException.class, () -> builder.toJson(summary));
    }
}

package com.g13.ftdms.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g13.ftdms.config.AiProperties;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.dto.StatsDtos;
import com.g13.ftdms.dto.UserDtos;
import com.g13.ftdms.entity.WorkoutLog;
import com.g13.ftdms.repository.WorkoutLogRepository;
import com.g13.ftdms.service.StatsService;
import com.g13.ftdms.service.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 分析摘要构建器。
 *
 * <p>把「先算后解释」落到实处：所有统计值先由 {@link StatsService} 按接口 F1–F3
 * 的同一套口径算出，再组装为 DS15 摘要。模型调用前后都不允许改写这些数值。</p>
 *
 * <p>有效天数按「饮食记录日 ∪ 训练记录日 ∪ 体重记录日」去重计算：
 * 任一类记录存在即视为该日有效，这与 SRS FR-28 的「区间内有效记录天数」一致。</p>
 */
@Component
public class AiSummaryBuilder {

    private final StatsService statsService;
    private final UserService userService;
    private final WorkoutLogRepository workoutLogRepository;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public AiSummaryBuilder(StatsService statsService, UserService userService,
                            WorkoutLogRepository workoutLogRepository,
                            ObjectMapper objectMapper, AiProperties properties) {
        this.statsService = statsService;
        this.userService = userService;
        this.workoutLogRepository = workoutLogRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 构建指定区间的输入摘要。
     *
     * @param userId     当前用户
     * @param periodDays 区间天数（7 或 30）
     * @param from       起始日（含）
     * @param to         结束日（含）
     * @return 摘要对象
     */
    public AiSummary build(Long userId, int periodDays, LocalDate from, LocalDate to) {
        StatsDtos.WorkoutStats workoutStats = statsService.workoutStats(userId, from, to);
        StatsDtos.IntakeStats intakeStats = statsService.intakeStats(userId, from, to);
        StatsDtos.WeightStats weightStats = statsService.weightStats(userId, from, to);
        UserDtos.ProfileResponse profile = userService.me(userId);
        List<WorkoutLog> workouts = workoutLogRepository
                .findByUserIdAndLogDateBetweenOrderByPerformedAtDesc(userId, from, to);

        Set<LocalDate> validDates = new HashSet<>();
        intakeStats.days().stream()
                .filter(StatsDtos.IntakeDay::recorded)
                .forEach(day -> validDates.add(day.date()));
        workouts.forEach(log -> validDates.add(log.getLogDate()));
        weightStats.points().forEach(point -> validDates.add(point.date()));

        Integer age = profile.birthDate() == null ? null
                : Period.between(profile.birthDate(), to).getYears();
        StatsDtos.IntakeAverage avg = intakeStats.avg();

        AiSummary.Profile summaryProfile = new AiSummary.Profile(
                profile.gender(), age, profile.heightCm(), weightStats.last());
        AiSummary.Goal summaryGoal = toGoal(intakeStats.target());
        AiSummary.Training training = new AiSummary.Training(
                workoutStats.plannedCount(), workoutStats.completedCount(), workoutStats.cancelledCount(),
                workoutStats.completionRate(), workouts.size(), workoutStats.extraWorkoutCount());
        AiSummary.Intake intake = new AiSummary.Intake(
                intakeStats.validDays(),
                avg == null ? null : avg.kcal(),
                avg == null ? null : avg.proteinG(),
                avg == null ? null : avg.carbG(),
                avg == null ? null : avg.fatG());
        AiSummary.Weight weight = new AiSummary.Weight(
                weightStats.points().size(), weightStats.first(), weightStats.last(),
                weightStats.change(), weightStats.sevenDayAvg());

        List<String> notices = new ArrayList<>();
        notices.add("缺失记录的日期不计入任何均值，也不按零值处理");
        notices.add("训练完成率的分母已剔除已取消计划");
        notices.add("摘要仅含统计值与基础档案，不含姓名、手机号与用户名");

        return new AiSummary(periodDays, from, to, validDates.size(), summaryProfile,
                summaryGoal, training, intake, weight, notices);
    }

    /**
     * 序列化摘要并校验 8 KB 上限。
     *
     * @param summary 摘要对象
     * @return JSON 字符串
     * @throws IllegalStateException 摘要超出配置上限（属于实现缺陷，不应由用户输入触发）
     */
    public String toJson(AiSummary summary) {
        try {
            String json = objectMapper.writeValueAsString(summary);
            int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > properties.getMaxSummaryBytes()) {
                throw new IllegalStateException("AI 摘要超过 " + properties.getMaxSummaryBytes() + " 字节：" + bytes);
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("AI 摘要序列化失败", ex);
        }
    }

    /** 目标实体 → 摘要目标；无生效目标时返回 null */
    private AiSummary.Goal toGoal(GoalDtos.GoalResponse target) {
        if (target == null) {
            return null;
        }
        return new AiSummary.Goal(target.kcal(), target.proteinG(), target.carbG(), target.fatG());
    }
}

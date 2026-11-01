package com.g13.ftdms.service;

import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.dto.StatsDtos;
import com.g13.ftdms.entity.BodyRecord;
import com.g13.ftdms.entity.DietRecord;
import com.g13.ftdms.entity.TrainingPlan;
import com.g13.ftdms.repository.BodyRecordRepository;
import com.g13.ftdms.repository.DietRecordRepository;
import com.g13.ftdms.repository.TrainingPlanRepository;
import com.g13.ftdms.repository.WorkoutLogRepository;
import com.g13.ftdms.service.support.NutritionCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计分析服务（接口族 F1–F3）。
 *
 * <p>统一口径（与《项目介绍》5.1/5.3 一致）：
 * <ul>
 *   <li><b>完成率</b> = 已完成计划数 ÷ (计划总数 − 已取消计划数)；分母为 0 时返回 null，显示"暂无计划"；</li>
 *   <li><b>缺失不补零</b>：没有记录的日期，摄入记为 null 且 recorded=false，不参与均值；</li>
 *   <li><b>七日平均</b>按区间末 7 天内的有效记录日计算，同时返回有效天数，避免"3 天数据当 7 天用"。</li>
 * </ul></p>
 */
@Service
public class StatsService {

    private final TrainingPlanRepository planRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final DietRecordRepository dietRepository;
    private final BodyRecordRepository bodyRepository;
    private final GoalService goalService;
    private final Clock clock;

    public StatsService(TrainingPlanRepository planRepository, WorkoutLogRepository workoutLogRepository,
                        DietRecordRepository dietRepository, BodyRecordRepository bodyRepository,
                        GoalService goalService, Clock clock) {
        this.planRepository = planRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.dietRepository = dietRepository;
        this.bodyRepository = bodyRepository;
        this.goalService = goalService;
        this.clock = clock;
    }

    /**
     * F1 训练完成率统计。
     *
     * @param userId 当前用户
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @return 计划数、完成数、取消数、完成率与临时训练次数
     */
    @Transactional(readOnly = true)
    public StatsDtos.WorkoutStats workoutStats(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);

        List<TrainingPlan> plans = planRepository
                .findByUserIdAndPlannedDateBetweenOrderByPlannedDateDescIdDesc(userId, from, to);
        int planned = plans.size();
        int completed = (int) plans.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        int cancelled = (int) plans.stream().filter(p -> "CANCELLED".equals(p.getStatus())).count();

        long extra = workoutLogRepository.countByUserIdAndLogDateBetweenAndPlanIdIsNull(userId, from, to);

        // 分母剔除已取消的计划；没有有效计划时完成率为 null（不是 0）
        int denominator = planned - cancelled;
        BigDecimal rate = null;
        if (denominator > 0) {
            rate = BigDecimal.valueOf(completed)
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        }
        return new StatsDtos.WorkoutStats(planned, completed, cancelled, rate, (int) extra);
    }

    /**
     * F2 每日摄入趋势。
     *
     * @param userId 当前用户
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @return 逐日摄入、有效日均值与区间末生效目标
     */
    @Transactional(readOnly = true)
    public StatsDtos.IntakeStats intakeStats(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);

        // 一次取出区间内全部记录，在内存按日期聚合成 map，避免逐日查询
        List<DietRecord> records = dietRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(userId, from, to);
        Map<LocalDate, BigDecimal> kcalMap = new HashMap<>();
        Map<LocalDate, BigDecimal> proteinMap = new HashMap<>();
        Map<LocalDate, BigDecimal> carbMap = new HashMap<>();
        Map<LocalDate, BigDecimal> fatMap = new HashMap<>();

        for (DietRecord r : records) {
            LocalDate d = r.getRecordDate();
            kcalMap.merge(d, nz(r.getSnapshotKcal()), BigDecimal::add);
            proteinMap.merge(d, nz(r.getSnapshotProteinG()), BigDecimal::add);
            carbMap.merge(d, nz(r.getSnapshotCarbG()), BigDecimal::add);
            fatMap.merge(d, nz(r.getSnapshotFatG()), BigDecimal::add);
        }

        List<StatsDtos.IntakeDay> days = new ArrayList<>();
        BigDecimal totalKcal = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarb = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean recorded = kcalMap.containsKey(d);
            BigDecimal dayKcal = recorded ? scale(kcalMap.get(d)) : null;
            days.add(new StatsDtos.IntakeDay(d, dayKcal, recorded));
            if (recorded) {
                totalKcal = totalKcal.add(nz(kcalMap.get(d)));
                totalProtein = totalProtein.add(nz(proteinMap.get(d)));
                totalCarb = totalCarb.add(nz(carbMap.get(d)));
                totalFat = totalFat.add(nz(fatMap.get(d)));
            }
        }

        int validDays = kcalMap.size();
        StatsDtos.IntakeAverage avg = null;
        if (validDays > 0) {
            avg = new StatsDtos.IntakeAverage(
                    NutritionCalculator.average(totalKcal, validDays),
                    NutritionCalculator.average(totalProtein, validDays),
                    NutritionCalculator.average(totalCarb, validDays),
                    NutritionCalculator.average(totalFat, validDays));
        }

        GoalDtos.GoalResponse target = goalService.current(userId, to);
        return new StatsDtos.IntakeStats(days, avg, validDays, target);
    }

    /**
     * F3 体重趋势。
     *
     * @param userId 当前用户
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @return 体重序列、首末值、变化量与最近七日平均
     */
    @Transactional(readOnly = true)
    public StatsDtos.WeightStats weightStats(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);

        List<BodyRecord> records = bodyRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(userId, from, to);
        List<StatsDtos.WeightPoint> points = records.stream()
                .map(r -> new StatsDtos.WeightPoint(r.getRecordDate(), r.getWeightKg()))
                .toList();

        BigDecimal first = points.isEmpty() ? null : points.get(0).weightKg();
        BigDecimal last = points.isEmpty() ? null : points.get(points.size() - 1).weightKg();
        BigDecimal change = (first == null || last == null) ? null : last.subtract(first);

        // 最近七日窗口：以查询结束日为终点向前推 6 天
        LocalDate weekStart = to.minusDays(6);
        List<BodyRecord> weekRecords = records.stream()
                .filter(r -> !r.getRecordDate().isBefore(weekStart))
                .toList();
        BigDecimal sevenDayAvg = null;
        if (!weekRecords.isEmpty()) {
            BigDecimal sum = weekRecords.stream()
                    .map(r -> nz(r.getWeightKg()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sevenDayAvg = sum.divide(BigDecimal.valueOf(weekRecords.size()), 2, RoundingMode.HALF_UP);
        }

        return new StatsDtos.WeightStats(points, first, last, change, sevenDayAvg, weekRecords.size());
    }

    /** 统一保留 2 位小数 */
    private BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** null 视为 0 */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

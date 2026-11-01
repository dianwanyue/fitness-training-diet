package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.DietDtos;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.entity.DietRecord;
import com.g13.ftdms.entity.Food;
import com.g13.ftdms.repository.DietRecordRepository;
import com.g13.ftdms.service.support.NutritionCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饮食记录服务（接口族 D3–D7）。
 *
 * <p>核心规则——<b>营养快照</b>：新增时按食物库的每 100 g 值乘以克重换算并存库；
 * 之后食物库改营养值、改品牌甚至停用，都不会影响历史记录的汇总结果。</p>
 *
 * <p>换算与汇总统一交给 {@link NutritionCalculator}，保证"单项计算"与"当日合计"
 * 使用同一套精度策略（先全精度相乘，最后统一保留 2 位小数）。</p>
 */
@Service
public class DietService {

    private static final Logger log = LoggerFactory.getLogger(DietService.class);

    /** 餐次固定四种，用于汇总结果补齐"空餐次"占位 */
    private static final List<String> MEALS = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final DietRecordRepository dietRepository;
    private final FoodService foodService;
    private final GoalService goalService;
    private final Clock clock;

    public DietService(DietRecordRepository dietRepository, FoodService foodService,
                       GoalService goalService, Clock clock) {
        this.dietRepository = dietRepository;
        this.foodService = foodService;
        this.goalService = goalService;
        this.clock = clock;
    }

    /**
     * 查询某日饮食记录。
     *
     * @param userId 当前用户
     * @param date   查询日期
     * @return 记录列表（含快照）
     */
    @Transactional(readOnly = true)
    public List<DietDtos.DietItem> listByDate(Long userId, LocalDate date) {
        return dietRepository.findByUserIdAndRecordDateOrderByIdAsc(userId, date).stream()
                .map(this::toItem)
                .toList();
    }

    /**
     * 新增饮食记录。
     *
     * @param userId  当前用户
     * @param request 录入内容
     * @return 含营养快照的写入结果
     * @throws BizException 1001 日期越界；1004 食物不可用
     */
    @Transactional
    public DietDtos.DietWriteResponse create(Long userId, DietDtos.DietCreateRequest request) {
        LocalDate recordDate = DateGuard.parseDate(request.recordDate(), "记录日期");
        DateGuard.requireRecent(recordDate, "记录日期", clock);

        Food food = foodService.loadVisible(userId, request.foodId());

        DietRecord record = new DietRecord();
        record.setUserId(userId);
        record.setRecordDate(recordDate);
        record.setMeal(request.meal());
        record.setFoodId(food.getId());
        record.setGrams(request.grams());
        applySnapshot(record, food);

        DietRecord saved = dietRepository.save(record);
        log.info("新增饮食记录 userId={} id={} foodId={} grams={} kcal={}",
                userId, saved.getId(), food.getId(), saved.getGrams(), saved.getSnapshotKcal());
        return toWriteResponse(saved);
    }

    /**
     * 修改饮食记录（只允许改餐次与克重）。
     *
     * <p>改克重时按"当前食物库的每 100 g 值"重算快照：这是刻意的选择——
     * 用户修改的是"当时吃了多少"，服务端用最新营养数据重新估算更符合直觉；
     * 若食物已被物理删除，则返回 1004 提示无法重算。</p>
     *
     * @param userId  当前用户
     * @param id      记录编号
     * @param request 修改内容
     * @return 更新后的记录
     * @throws BizException 1004 记录或食物不存在
     */
    @Transactional
    public DietDtos.DietWriteResponse update(Long userId, Long id, DietDtos.DietUpdateRequest request) {
        DietRecord record = dietRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "饮食记录不存在或不属于本人"));

        if (request.meal() != null) {
            record.setMeal(request.meal());
        }
        if (request.grams() != null) {
            record.setGrams(request.grams());
            // 克重变化必须以原食物的营养值为基准重算快照
            Food food = foodService.loadForRecalculate(record.getFoodId());
            applySnapshot(record, food);
        }

        DietRecord saved = dietRepository.save(record);
        log.info("修改饮食记录 userId={} id={}", userId, id);
        return toWriteResponse(saved);
    }

    /**
     * 删除饮食记录。
     *
     * @param userId 当前用户
     * @param id     记录编号
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional
    public void delete(Long userId, Long id) {
        DietRecord record = dietRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "饮食记录不存在或不属于本人"));
        dietRepository.delete(record);
        log.info("删除饮食记录 userId={} id={}", userId, id);
    }

    /**
     * 当日饮食汇总。
     *
     * <p>返回：四项合计 + 按餐次分组的明细 + 当日生效目标。
     * 目标缺失时 {@code target} 为 null（前端隐藏进度环），不做补零。</p>
     *
     * @param userId 当前用户
     * @param date   汇总日期
     * @return 汇总结果
     */
    @Transactional(readOnly = true)
    public DietDtos.DietSummary summary(Long userId, LocalDate date) {
        List<DietRecord> records = dietRepository.findByUserIdAndRecordDateOrderByIdAsc(userId, date);

        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carb = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;

        // 四个餐次固定顺序，即使当天某餐没记录也返回空数组，前端渲染稳定
        Map<String, List<DietDtos.DietItem>> meals = new LinkedHashMap<>();
        MEALS.forEach(m -> meals.put(m, new ArrayList<>()));

        for (DietRecord r : records) {
            kcal = kcal.add(nz(r.getSnapshotKcal()));
            protein = protein.add(nz(r.getSnapshotProteinG()));
            carb = carb.add(nz(r.getSnapshotCarbG()));
            fat = fat.add(nz(r.getSnapshotFatG()));
            meals.computeIfAbsent(r.getMeal(), k -> new ArrayList<>()).add(toItem(r));
        }

        GoalDtos.GoalResponse target = goalService.current(userId, date);
        return new DietDtos.DietSummary(date,
                NutritionCalculator.sum(kcal), NutritionCalculator.sum(protein),
                NutritionCalculator.sum(carb), NutritionCalculator.sum(fat),
                target, meals);
    }

    /**
     * 按食物每 100 g 值写入四项快照。
     *
     * @param record 待写入的记录
     * @param food   食物（提供每 100 g 营养值）
     */
    private void applySnapshot(DietRecord record, Food food) {
        record.setSnapshotKcal(NutritionCalculator.amount(food.getKcalPer100g(), record.getGrams()));
        record.setSnapshotProteinG(NutritionCalculator.amount(food.getProteinPer100g(), record.getGrams()));
        record.setSnapshotCarbG(NutritionCalculator.amount(food.getCarbPer100g(), record.getGrams()));
        record.setSnapshotFatG(NutritionCalculator.amount(food.getFatPer100g(), record.getGrams()));
        record.setSnapshotSource(food.getSource());
    }

    /** 实体 → 列表项视图 */
    private DietDtos.DietItem toItem(DietRecord r) {
        return new DietDtos.DietItem(r.getId(), r.getMeal(), r.getFoodId(), foodName(r.getFoodId()),
                r.getGrams(), r.getSnapshotKcal(), r.getSnapshotProteinG(),
                r.getSnapshotCarbG(), r.getSnapshotFatG());
    }

    /**
     * 取食物名称用于展示。
     *
     * <p>列表页需要 foodName，但历史记录关联的食物可能已停用；此时仍应展示名称，
     * 只有食物被物理删除才退化为占位文案。</p>
     */
    private String foodName(Long foodId) {
        try {
            return foodService.nameOf(foodId);
        } catch (BizException ex) {
            return "已删除的食物";
        }
    }

    /** 写入结果视图 */
    private DietDtos.DietWriteResponse toWriteResponse(DietRecord r) {
        return new DietDtos.DietWriteResponse(r.getId(), r.getRecordDate(), r.getMeal(), r.getGrams(),
                r.getSnapshotKcal(), r.getSnapshotProteinG(), r.getSnapshotCarbG(), r.getSnapshotFatG());
    }

    /** null 视为 0（快照列非空，历史数据理论上不会有 null，这里做防御） */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

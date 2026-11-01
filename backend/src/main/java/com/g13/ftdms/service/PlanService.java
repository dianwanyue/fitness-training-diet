package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.PlanDtos;
import com.g13.ftdms.entity.Exercise;
import com.g13.ftdms.entity.PlanItem;
import com.g13.ftdms.entity.TrainingPlan;
import com.g13.ftdms.repository.PlanItemRepository;
import com.g13.ftdms.repository.TrainingPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 训练计划服务（接口族 C2–C6）。
 *
 * <p>设计要点：
 * <ol>
 *   <li><b>整单替换</b>：修改计划时直接替换动作清单，前端不必做增量 diff；</li>
 *   <li><b>状态单向</b>：PLANNED → COMPLETED / CANCELLED，禁止回退，保证完成率口径稳定；</li>
 *   <li><b>不改写历史</b>：修改计划只动 plan_item，不触碰 workout_log/set，
 *       已有训练记录永远保留当时的实际情况。</li>
 * </ol></p>
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final TrainingPlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final ExerciseService exerciseService;
    private final Clock clock;

    public PlanService(TrainingPlanRepository planRepository, PlanItemRepository planItemRepository,
                       ExerciseService exerciseService, Clock clock) {
        this.planRepository = planRepository;
        this.planItemRepository = planItemRepository;
        this.exerciseService = exerciseService;
        this.clock = clock;
    }

    /**
     * 查询计划列表。
     *
     * @param userId 当前用户
     * @param from   起始日期（含）
     * @param to     结束日期（含）
     * @param status 状态过滤，空表示全部
     * @return 计划概要列表（含动作条数）
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.PlanSummary> list(Long userId, LocalDate from, LocalDate to, String status) {
        DateGuard.requireRange(from, to);
        List<TrainingPlan> plans = planRepository
                .findByUserIdAndPlannedDateBetweenOrderByPlannedDateDescIdDesc(userId, from, to);

        if (status != null && !status.isBlank()) {
            plans = plans.stream().filter(p -> status.equals(p.getStatus())).toList();
        }
        if (plans.isEmpty()) {
            return List.of();
        }

        // 一次性把所有明细查出来再分组，避免逐条计划查询造成的 N+1
        Map<Long, Long> countMap = planItemRepository
                .findByPlanIdIn(plans.stream().map(TrainingPlan::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(PlanItem::getPlanId, Collectors.counting()));

        return plans.stream()
                .map(p -> new PlanDtos.PlanSummary(p.getId(), p.getPlannedDate(), p.getTitle(), p.getStatus(),
                        countMap.getOrDefault(p.getId(), 0L).intValue()))
                .toList();
    }

    /**
     * 查询计划详情。
     *
     * @param userId 当前用户
     * @param planId 计划编号
     * @return 计划头 + 动作明细（含动作名称）
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional(readOnly = true)
    public PlanDtos.PlanDetail detail(Long userId, Long planId) {
        TrainingPlan plan = loadOwned(userId, planId);
        List<PlanItem> items = planItemRepository.findByPlanIdOrderByOrderNoAscIdAsc(planId);
        Map<Long, String> nameMap = exerciseNames(items.stream().map(PlanItem::getExerciseId).toList());
        List<PlanDtos.PlanItemView> views = items.stream()
                .map(i -> new PlanDtos.PlanItemView(i.getExerciseId(), nameMap.get(i.getExerciseId()),
                        i.getTargetSets(), i.getTargetReps(), i.getTargetWeightKg(), i.getOrderNo()))
                .toList();
        return new PlanDtos.PlanDetail(plan.getId(), plan.getPlannedDate(), plan.getTitle(),
                plan.getStatus(), plan.getNote(), views);
    }

    /**
     * 新建计划。
     *
     * @param userId  当前用户
     * @param request 计划内容
     * @return 新建结果（初始状态 PLANNED）
     * @throws BizException 1001 日期越界；1004 动作不可用
     */
    @Transactional
    public PlanDtos.PlanCreateResponse create(Long userId, PlanDtos.PlanSaveRequest request) {
        LocalDate plannedDate = DateGuard.parseDate(request.plannedDate(), "计划日期");
        DateGuard.requireRecent(plannedDate, "计划日期", clock);

        // 先校验动作可用性再落库，避免"计划建好但明细无效"的中间状态
        Map<Long, Exercise> exerciseMap = exerciseService.loadUsable(
                request.items().stream().map(PlanDtos.PlanItemRequest::exerciseId).collect(Collectors.toSet()),
                userId);

        TrainingPlan plan = new TrainingPlan();
        plan.setUserId(userId);
        plan.setPlannedDate(plannedDate);
        plan.setTitle(normalize(request.title()));
        plan.setNote(normalize(request.note()));
        plan.setStatus("PLANNED");
        TrainingPlan saved = planRepository.save(plan);

        saveItems(saved.getId(), request.items(), exerciseMap);
        log.info("新建计划 userId={} planId={} items={}", userId, saved.getId(), request.items().size());
        return new PlanDtos.PlanCreateResponse(saved.getId(), saved.getPlannedDate(), saved.getTitle(), saved.getStatus());
    }

    /**
     * 修改计划（整单替换动作清单）。
     *
     * @param userId  当前用户
     * @param planId  计划编号
     * @param request 新的计划内容
     * @return 修改后的状态
     * @throws BizException 1004 不存在；1005 已完成或已取消；1001 日期越界
     */
    @Transactional
    public PlanDtos.PlanStatusResponse update(Long userId, Long planId, PlanDtos.PlanSaveRequest request) {
        TrainingPlan plan = loadOwned(userId, planId);
        if (!plan.isEditable()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "计划已完成或已取消，不可修改");
        }
        LocalDate plannedDate = DateGuard.parseDate(request.plannedDate(), "计划日期");
        DateGuard.requireRecent(plannedDate, "计划日期", clock);

        Map<Long, Exercise> exerciseMap = exerciseService.loadUsable(
                request.items().stream().map(PlanDtos.PlanItemRequest::exerciseId).collect(Collectors.toSet()),
                userId);

        plan.setPlannedDate(plannedDate);
        plan.setTitle(normalize(request.title()));
        plan.setNote(normalize(request.note()));
        planRepository.save(plan);

        // 整单替换：先清空旧明细再写入新明细，同一事务内完成
        planItemRepository.deleteByPlanId(planId);
        saveItems(planId, request.items(), exerciseMap);
        log.info("修改计划 userId={} planId={}", userId, planId);

        return new PlanDtos.PlanStatusResponse(plan.getId(), plan.getStatus(), plan.getUpdatedAt());
    }

    /**
     * 变更计划状态（完成 / 取消）。
     *
     * @param userId  当前用户
     * @param planId  计划编号
     * @param status  目标状态：COMPLETED 或 CANCELLED
     * @return 变更后的状态
     * @throws BizException 1004 不存在；1005 当前状态不允许变更
     */
    @Transactional
    public PlanDtos.PlanStatusResponse changeStatus(Long userId, Long planId, String status) {
        TrainingPlan plan = loadOwned(userId, planId);
        if (!"PLANNED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "只有待执行的计划可以变更为" + ("COMPLETED".equals(status) ? "已完成" : "已取消"));
        }
        plan.setStatus(status);
        TrainingPlan saved = planRepository.save(plan);
        log.info("计划状态变更 userId={} planId={} status={}", userId, planId, status);
        return new PlanDtos.PlanStatusResponse(saved.getId(), saved.getStatus(), saved.getUpdatedAt());
    }

    /**
     * 内部能力：按编号 + 归属加载计划。
     *
     * @param userId 当前用户
     * @param planId 计划编号
     * @return 计划实体
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional(readOnly = true)
    public TrainingPlan loadOwned(Long userId, Long planId) {
        return planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "计划不存在或不属于本人"));
    }

    /** 写入计划明细；orderNo 缺省时按数组顺序补齐 */
    private void saveItems(Long planId, List<PlanDtos.PlanItemRequest> items, Map<Long, Exercise> exerciseMap) {
        List<PlanItem> entities = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            PlanDtos.PlanItemRequest req = items.get(i);
            PlanItem item = new PlanItem();
            item.setPlanId(planId);
            item.setExerciseId(req.exerciseId());
            item.setTargetSets(req.targetSets());
            item.setTargetReps(req.targetReps());
            item.setTargetWeightKg(req.targetWeightKg());
            item.setOrderNo(req.orderNo() == null ? i : req.orderNo());
            entities.add(item);
        }
        planItemRepository.saveAll(entities);
    }

    /** 空串归一化为 null：标题/备注为空时应存 null 而不是空字符串，避免前端出现空行 */
    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 批量取动作名称，缺失时给出占位文案（动作被物理删除的历史数据场景） */
    private Map<Long, String> exerciseNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>(exerciseService.nameMap(ids));
        // 动作被物理删除时仍要让页面可读，用占位符代替空白
        ids.forEach(id -> map.putIfAbsent(id, "已删除的动作"));
        return map;
    }
}

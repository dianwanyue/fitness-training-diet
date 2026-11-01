package com.g13.ftdms.service;

import com.g13.ftdms.common.api.ErrorCode;
import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.WorkoutDtos;
import com.g13.ftdms.entity.WorkoutLog;
import com.g13.ftdms.entity.WorkoutSet;
import com.g13.ftdms.repository.WorkoutLogRepository;
import com.g13.ftdms.repository.WorkoutSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 训练记录服务（接口族 C7–C9）。
 *
 * <p>核心规则：
 * <ul>
 *   <li>记录保存"实际完成"，允许与计划的组数、重量不一致；</li>
 *   <li>{@code planId} 可空——不按计划也能记录（临时训练），统计时单列；</li>
 *   <li>多表写入（log + set）在同一事务内完成，避免出现"有记录无明细"的脏数据；</li>
 *   <li>归属日期按业务时区由 {@code performedAt} 截断，保证跨零点训练归到正确的一天。</li>
 * </ul></p>
 */
@Service
public class WorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);

    /** 允许的时钟误差：训练时间不得晚于当前时刻 5 分钟 */
    private static final long FUTURE_TOLERANCE_MINUTES = 5;

    private final WorkoutLogRepository logRepository;
    private final WorkoutSetRepository setRepository;
    private final ExerciseService exerciseService;
    private final PlanService planService;
    private final Clock clock;

    public WorkoutService(WorkoutLogRepository logRepository, WorkoutSetRepository setRepository,
                          ExerciseService exerciseService, PlanService planService, Clock clock) {
        this.logRepository = logRepository;
        this.setRepository = setRepository;
        this.exerciseService = exerciseService;
        this.planService = planService;
        this.clock = clock;
    }

    /**
     * 查询训练记录列表（含组明细）。
     *
     * @param userId 当前用户
     * @param from   归属日期起始（含）
     * @param to     归属日期结束（含）
     * @return 记录列表，按训练时间倒序
     */
    @Transactional(readOnly = true)
    public List<WorkoutDtos.WorkoutView> list(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);
        List<WorkoutLog> logs = logRepository.findByUserIdAndLogDateBetweenOrderByPerformedAtDesc(userId, from, to);
        if (logs.isEmpty()) {
            return List.of();
        }

        // 一次取出全部组明细并按 logId 分组，避免逐条查询
        Map<Long, List<WorkoutSet>> setMap = setRepository
                .findByLogIdInOrderBySetNoAscIdAsc(logs.stream().map(WorkoutLog::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkoutSet::getLogId));

        Map<Long, String> nameMap = exerciseService.nameMap(
                setMap.values().stream().flatMap(List::stream).map(WorkoutSet::getExerciseId).collect(Collectors.toSet()));

        return logs.stream().map(log -> {
            List<WorkoutDtos.WorkoutSetView> sets = setMap.getOrDefault(log.getId(), List.of()).stream()
                    .map(s -> new WorkoutDtos.WorkoutSetView(s.getExerciseId(),
                            nameMap.getOrDefault(s.getExerciseId(), "已删除的动作"),
                            s.getSetNo(), s.getWeightKg(), s.getReps()))
                    .toList();
            return new WorkoutDtos.WorkoutView(log.getId(), log.getPlanId(), log.getPerformedAt(),
                    log.getLogDate(), log.getNote(), sets);
        }).toList();
    }

    /**
     * 新增训练记录。
     *
     * @param userId  当前用户
     * @param request 记录内容
     * @return 记录编号与归属日期
     * @throws BizException 1001 时间非法；1004 计划或动作不可用
     */
    @Transactional
    public WorkoutDtos.WorkoutCreateResponse create(Long userId, WorkoutDtos.WorkoutCreateRequest request) {
        LocalDateTime performedAt = DateGuard.parseDateTime(request.performedAt(), "训练时间");
        LocalDateTime now = LocalDateTime.now(clock);
        if (performedAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw BizException.param("训练时间不得晚于当前时间");
        }

        // 关联计划必须属于本人；不传 planId 表示临时训练
        if (request.planId() != null) {
            planService.loadOwned(userId, request.planId());
        }

        // 组明细里的动作必须可用，一次性批量校验
        exerciseService.loadUsable(
                request.sets().stream().map(WorkoutDtos.WorkoutSetRequest::exerciseId).collect(Collectors.toSet()),
                userId);

        WorkoutLog logEntity = new WorkoutLog();
        logEntity.setUserId(userId);
        logEntity.setPlanId(request.planId());
        logEntity.setPerformedAt(performedAt);
        // 归属日期由业务时区的 performedAt 直接截断，避免再引入一次时区换算
        logEntity.setLogDate(performedAt.toLocalDate());
        logEntity.setNote(request.note());
        WorkoutLog savedLog = logRepository.save(logEntity);

        List<WorkoutSet> sets = new ArrayList<>(request.sets().size());
        for (int i = 0; i < request.sets().size(); i++) {
            WorkoutDtos.WorkoutSetRequest req = request.sets().get(i);
            WorkoutSet set = new WorkoutSet();
            set.setLogId(savedLog.getId());
            set.setExerciseId(req.exerciseId());
            set.setSetNo(req.setNo() == null ? i + 1 : req.setNo());
            set.setWeightKg(req.weightKg());
            set.setReps(req.reps());
            set.setCompleted(1);
            sets.add(set);
        }
        setRepository.saveAll(sets);

        log.info("新增训练记录 userId={} logId={} sets={} planId={}",
                userId, savedLog.getId(), sets.size(), request.planId());
        return new WorkoutDtos.WorkoutCreateResponse(savedLog.getId(), savedLog.getLogDate(), savedLog.getPlanId());
    }

    /**
     * 删除训练记录及其组明细。
     *
     * @param userId 当前用户
     * @param logId  记录编号
     * @throws BizException 1004 不存在或不属于本人
     */
    @Transactional
    public void delete(Long userId, Long logId) {
        WorkoutLog logEntity = logRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "训练记录不存在或不属于本人"));
        // 先删子表再删主表，避免逻辑外键悬挂
        setRepository.deleteByLogId(logEntity.getId());
        logRepository.delete(logEntity);
        log.info("删除训练记录 userId={} logId={}", userId, logId);
    }
}

package com.g13.ftdms.service;

import com.g13.ftdms.common.exception.BizException;
import com.g13.ftdms.common.util.DateGuard;
import com.g13.ftdms.dto.GoalDtos;
import com.g13.ftdms.entity.DailyGoal;
import com.g13.ftdms.repository.DailyGoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日目标服务（接口族 B1–B3）。
 *
 * <p>核心规则：
 * <ul>
 *   <li><b>按生效日期留历史</b>：不同日期可以有不同目标，查询某日目标时取"不晚于该日的最近一条"；</li>
 *   <li><b>同日覆盖</b>：同一生效日期重复提交视为修改，不做成两条；</li>
 *   <li><b>缺失不补零</b>：没有目标时返回 null，由前端显示"未设置"，而不是 0 kcal。</li>
 * </ul></p>
 */
@Service
public class GoalService {

    private static final Logger log = LoggerFactory.getLogger(GoalService.class);

    private final DailyGoalRepository goalRepository;
    private final Clock clock;

    public GoalService(DailyGoalRepository goalRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.clock = clock;
    }

    /**
     * 查询某日生效的目标。
     *
     * @param userId 当前用户
     * @param date   查询日期，null 表示今天
     * @return 目标视图；未设置时返回 null
     */
    @Transactional(readOnly = true)
    public GoalDtos.GoalResponse current(Long userId, LocalDate date) {
        LocalDate queryDate = (date == null) ? LocalDate.now(clock) : date;
        return goalRepository
                .findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(userId, queryDate)
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * 内部复用：取某日生效目标实体（饮食汇总、统计接口需要目标值）。
     *
     * @param userId 当前用户
     * @param date   查询日期
     * @return 目标实体，可为空
     */
    @Transactional(readOnly = true)
    public DailyGoal findEffective(Long userId, LocalDate date) {
        return goalRepository
                .findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(userId, date)
                .orElse(null);
    }

    /**
     * 新增或覆盖目标。
     *
     * @param userId  当前用户
     * @param request 目标内容
     * @return 保存后的目标
     * @throws BizException 1001 生效日期越界
     */
    @Transactional
    public GoalDtos.GoalResponse upsert(Long userId, GoalDtos.GoalRequest request) {
        LocalDate effectiveDate = DateGuard.parseDate(request.effectiveDate(), "生效日期");
        // 目标允许提前 7 天设置，也允许补录过去一年的目标，与饮食/体重保持同一口径
        DateGuard.requireRecent(effectiveDate, "生效日期", clock);

        DailyGoal goal = goalRepository.findByUserIdAndEffectiveDate(userId, effectiveDate)
                .orElseGet(DailyGoal::new);
        boolean isNew = goal.getId() == null;
        goal.setUserId(userId);
        goal.setEffectiveDate(effectiveDate);
        goal.setKcal(request.kcal());
        goal.setProteinG(nullToZero(request.proteinG()));
        goal.setCarbG(nullToZero(request.carbG()));
        goal.setFatG(nullToZero(request.fatG()));

        DailyGoal saved = goalRepository.save(goal);
        log.info("{}目标 userId={} date={} kcal={}", isNew ? "新增" : "覆盖", userId, effectiveDate, saved.getKcal());
        return toResponse(saved);
    }

    /**
     * 查询目标历史。
     *
     * @param userId 当前用户
     * @param from   起始生效日期（含）
     * @param to     结束生效日期（含）
     * @return 按生效日期升序的版本列表
     */
    @Transactional(readOnly = true)
    public List<GoalDtos.GoalResponse> history(Long userId, LocalDate from, LocalDate to) {
        DateGuard.requireRange(from, to);
        return goalRepository.findByUserIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(userId, from, to)
                .stream().map(this::toResponse).toList();
    }

    /** 实体 → 视图 */
    private GoalDtos.GoalResponse toResponse(DailyGoal goal) {
        return new GoalDtos.GoalResponse(goal.getId(), goal.getEffectiveDate(), goal.getKcal(),
                goal.getProteinG(), goal.getCarbG(), goal.getFatG());
    }

    /** 三大营养素缺省按 0 处理（与表结构默认值一致） */
    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

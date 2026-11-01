package com.g13.ftdms.repository;

import com.g13.ftdms.entity.DailyGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 每日目标仓储。
 *
 * <p>核心查询是"某日生效的目标"：取生效日期 ≤ 该日期中最近的一条，
 * 由方法名 {@code findFirstBy...LessThanEqualOrderByEffectiveDateDesc} 表达，
 * 语义直观且能命中 (user_id, effective_date) 唯一索引。</p>
 */
public interface DailyGoalRepository extends JpaRepository<DailyGoal, Long> {

    /** 精确匹配某生效日期（写入时判断"同日覆盖"） */
    Optional<DailyGoal> findByUserIdAndEffectiveDate(Long userId, LocalDate effectiveDate);

    /** 某日生效的目标：生效日期不晚于该日期的最近一条；无则返回空（不补零） */
    Optional<DailyGoal> findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            Long userId, LocalDate date);

    /** 目标历史：按生效日期升序返回区间内的所有版本 */
    List<DailyGoal> findByUserIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            Long userId, LocalDate from, LocalDate to);
}

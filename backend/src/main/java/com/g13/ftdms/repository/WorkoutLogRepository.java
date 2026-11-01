package com.g13.ftdms.repository;

import com.g13.ftdms.entity.WorkoutLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 训练记录仓储。
 *
 * <p>统计口径相关：本表的日期字段 {@code logDate} 是冗余的"归属日期"，
 * 因此区间统计可以直接走 (user_id, log_date) 索引，不必对时间戳做函数运算。</p>
 */
public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    /** 按归属日期区间查询本人训练记录（时间倒序） */
    List<WorkoutLog> findByUserIdAndLogDateBetweenOrderByPerformedAtDesc(
            Long userId, LocalDate from, LocalDate to);

    /** 按主键 + 归属查询 */
    Optional<WorkoutLog> findByIdAndUserId(Long id, Long userId);

    /** 区间内临时训练次数（planId 为空），统计接口的独立指标 */
    long countByUserIdAndLogDateBetweenAndPlanIdIsNull(Long userId, LocalDate from, LocalDate to);

    /** 区间内训练记录总数 */
    long countByUserIdAndLogDateBetween(Long userId, LocalDate from, LocalDate to);
}

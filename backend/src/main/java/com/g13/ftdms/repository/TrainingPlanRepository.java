package com.g13.ftdms.repository;

import com.g13.ftdms.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 训练计划仓储。
 *
 * <p>所有查询都带 {@code userId} 条件——这是跨账号隔离的第二道防线：
 * 即使上层忘记校验归属，也查不到别人的数据。</p>
 */
public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    /** 按日期区间查询本人计划（新日期在前） */
    List<TrainingPlan> findByUserIdAndPlannedDateBetweenOrderByPlannedDateDescIdDesc(
            Long userId, LocalDate from, LocalDate to);

    /** 按主键 + 归属查询，越权时返回空（对外统一表现为 1004） */
    Optional<TrainingPlan> findByIdAndUserId(Long id, Long userId);

    /** 区间内计划总数（完成率分母的基础值） */
    long countByUserIdAndPlannedDateBetween(Long userId, LocalDate from, LocalDate to);

    /** 区间内指定状态的计划数（已完成计入分子，已取消从分母剔除） */
    long countByUserIdAndPlannedDateBetweenAndStatus(Long userId, LocalDate from, LocalDate to, String status);
}

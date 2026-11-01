package com.g13.ftdms.repository;

import com.g13.ftdms.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 计划动作仓储。
 *
 * <p>计划动作是计划的从属数据：更新计划时采用"先删后插"的整单替换策略，
 * 因此需要按 planId 批量删除。</p>
 */
public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

    /** 查询某计划的全部动作，按排序号升序 */
    List<PlanItem> findByPlanIdOrderByOrderNoAscIdAsc(Long planId);

    /** 批量查询多个计划的动作（列表页计算 itemCount 时避免 N+1 查询） */
    List<PlanItem> findByPlanIdIn(Collection<Long> planIds);

    /** 整单替换前清空旧明细 */
    void deleteByPlanId(Long planId);
}

package com.g13.ftdms.repository;

import com.g13.ftdms.entity.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 训练组明细仓储。
 *
 * <p>组明细与训练记录是一对多，写入在同一事务内完成；
 * 删除训练记录时先删组明细，避免留下孤儿数据。</p>
 */
public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    /** 查询某条训练记录的全部组明细 */
    List<WorkoutSet> findByLogIdOrderBySetNoAscIdAsc(Long logId);

    /** 批量查询多条记录的组明细（列表页一次性装配，避免 N+1） */
    List<WorkoutSet> findByLogIdInOrderBySetNoAscIdAsc(Collection<Long> logIds);

    /** 删除某条记录的全部组明细 */
    void deleteByLogId(Long logId);
}

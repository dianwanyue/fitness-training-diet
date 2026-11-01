package com.g13.ftdms.repository;

import com.g13.ftdms.entity.DietRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 饮食记录仓储。
 *
 * <p>查询以"用户 + 日期"为主键组合，对应索引 {@code idx_diet_user_date}；
 * 区间查询用于统计接口的每日摄入趋势。</p>
 */
public interface DietRecordRepository extends JpaRepository<DietRecord, Long> {

    /** 某日全部饮食记录（按录入顺序返回，前端按餐次分组展示） */
    List<DietRecord> findByUserIdAndRecordDateOrderByIdAsc(Long userId, LocalDate date);

    /** 按主键 + 归属查询 */
    Optional<DietRecord> findByIdAndUserId(Long id, Long userId);

    /** 区间内饮食记录（用于每日摄入趋势与日均值计算） */
    List<DietRecord> findByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(
            Long userId, LocalDate from, LocalDate to);
}

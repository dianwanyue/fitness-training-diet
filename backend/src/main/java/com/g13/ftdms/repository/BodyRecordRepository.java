package com.g13.ftdms.repository;

import com.g13.ftdms.entity.BodyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 体重记录仓储。
 *
 * <p>唯一约束保证"同一用户同一天只有一条"，因此按日期查询最多返回一条，
 * 写入时先查后改即可实现 UPSERT 语义。</p>
 */
public interface BodyRecordRepository extends JpaRepository<BodyRecord, Long> {

    /** 按用户 + 日期查询（写入前的存在性判断） */
    Optional<BodyRecord> findByUserIdAndRecordDate(Long userId, LocalDate date);

    /** 按主键 + 归属查询 */
    Optional<BodyRecord> findByIdAndUserId(Long id, Long userId);

    /** 区间内体重序列（升序，用于趋势图） */
    List<BodyRecord> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
            Long userId, LocalDate from, LocalDate to);
}

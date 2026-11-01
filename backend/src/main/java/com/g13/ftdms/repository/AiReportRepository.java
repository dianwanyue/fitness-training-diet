package com.g13.ftdms.repository;

import com.g13.ftdms.entity.AiReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AI 分析报告仓储（数据层已就位，接口在 W7 实现）。
 *
 * <p>报告按生成时间倒序分页；详情与删除同样带 userId 条件，保证只能操作本人报告。</p>
 */
public interface AiReportRepository extends JpaRepository<AiReport, Long> {

    /** 本人报告分页列表（生成时间倒序） */
    Page<AiReport> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 按主键 + 归属查询 */
    Optional<AiReport> findByIdAndUserId(Long id, Long userId);
}

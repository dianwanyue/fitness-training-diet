package com.g13.ftdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 分析报告实体（表 {@code ai_report}），对应数据字典 D12。
 *
 * <p>保存三样东西：输入模型的原始摘要（{@code summaryJson}，用于数值核对与追溯）、
 * 模型输出文本、模型名称与版本。{@code status} 支持 DEGRADED——模型不可用时
 * 仍保留统计值，避免"什么都看不到"。</p>
 */
@Entity
@Table(name = "ai_report")
@Getter
@Setter
@NoArgsConstructor
public class AiReport {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 分析区间起始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 分析区间结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 输入模型的结构化摘要（JSON 字符串，整体 ≤8 KB） */
    @Column(name = "summary_json", nullable = false, length = 8000)
    private String summaryJson;

    /** 模型生成的分析文本（五段式） */
    @Column(length = 8000)
    private String content;

    /** 模型名称 */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /** 模型版本标识 */
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    /** 报告状态：SUCCESS / FAILED / DEGRADED */
    @Column(nullable = false, length = 16)
    private String status = "SUCCESS";

    /** 生成时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

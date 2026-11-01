package com.g13.ftdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 体重记录实体（表 {@code body_record}），对应数据字典 D10。
 *
 * <p>约束：同一用户同一日期只保留一条（唯一索引 {@code uk_body_user_date}），
 * 重复提交按"更新"处理，因此体重序列天然是"每日一个点"，
 * 不存在同一天两个体重导致的趋势抖动。</p>
 */
@Entity
@Table(name = "body_record")
@Getter
@Setter
@NoArgsConstructor
public class BodyRecord {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 记录日期（业务时区） */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 体重（kg），20–300 */
    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    /** 备注 */
    @Column(length = 500)
    private String note;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

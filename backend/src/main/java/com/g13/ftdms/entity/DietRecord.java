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
 * 饮食记录实体（表 {@code diet_record}），对应数据字典 D9。
 *
 * <p>关键设计——<b>营养快照</b>：写入时按"每 100 g 值 × 克重 ÷ 100"算出四项营养值并落库，
 * 之后即使食物库调整了营养值，历史汇总也不会被改写。{@code snapshotSource} 同时记下来源，
 * 便于日后核对数据出处。</p>
 */
@Entity
@Table(name = "diet_record")
@Getter
@Setter
@NoArgsConstructor
public class DietRecord {

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

    /** 餐次：BREAKFAST / LUNCH / DINNER / SNACK */
    @Column(nullable = false, length = 16)
    private String meal = "BREAKFAST";

    /** 关联食物 */
    @Column(name = "food_id", nullable = false)
    private Long foodId;

    /** 实际摄入克重（g），1–2000 */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal grams;

    /** 快照：本项能量（kcal） */
    @Column(name = "snapshot_kcal", nullable = false, precision = 10, scale = 2)
    private BigDecimal snapshotKcal = BigDecimal.ZERO;

    /** 快照：本项蛋白质（g） */
    @Column(name = "snapshot_protein_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal snapshotProteinG = BigDecimal.ZERO;

    /** 快照：本项碳水化合物（g） */
    @Column(name = "snapshot_carb_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal snapshotCarbG = BigDecimal.ZERO;

    /** 快照：本项脂肪（g） */
    @Column(name = "snapshot_fat_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal snapshotFatG = BigDecimal.ZERO;

    /** 快照：录入时的营养数据来源 */
    @Column(name = "snapshot_source", length = 255)
    private String snapshotSource;

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

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
 * 每日能量与营养目标实体（表 {@code daily_goal}），对应数据字典 D2。
 *
 * <p>按 {@code effective_date} 保留历史：查询某日的目标时，取生效日期不晚于该日期的最近一条。
 * 同一用户同一生效日期只允许一条（唯一约束），重复提交按覆盖处理——这样既保留调整轨迹，
 * 又避免同一天出现两个互相矛盾的目标。</p>
 */
@Entity
@Table(name = "daily_goal")
@Getter
@Setter
@NoArgsConstructor
public class DailyGoal {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 生效日期（业务时区） */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** 每日能量目标（kcal），500–6000 */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal kcal;

    /** 蛋白质目标（g），0–1000 */
    @Column(name = "protein_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinG = BigDecimal.ZERO;

    /** 碳水化合物目标（g） */
    @Column(name = "carb_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbG = BigDecimal.ZERO;

    /** 脂肪目标（g） */
    @Column(name = "fat_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatG = BigDecimal.ZERO;

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

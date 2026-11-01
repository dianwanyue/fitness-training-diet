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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 训练计划实体（表 {@code training_plan}），对应数据字典 D4。
 *
 * <p>计划描述"准备执行"的内容，与"实际完成"的训练记录分开存放。
 * 状态单向流转：PLANNED → COMPLETED 或 CANCELLED，不允许回退，
 * 这样完成率的分子分母才有确定含义（取消的计划不计入分母）。</p>
 */
@Entity
@Table(name = "training_plan")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlan {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 计划日期（业务时区） */
    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    /** 计划标题，如"上肢推日" */
    @Column(length = 100)
    private String title;

    /** 状态：PLANNED 待执行 / COMPLETED 已完成 / CANCELLED 已取消 */
    @Column(nullable = false, length = 16)
    private String status = "PLANNED";

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

    /** 是否仍可编辑：只有待执行的计划允许修改 */
    public boolean isEditable() {
        return "PLANNED".equals(status);
    }
}

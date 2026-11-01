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

import java.time.LocalDateTime;

/**
 * 动作库实体（表 {@code exercise}），对应数据字典 D3。
 *
 * <p>公共动作由管理员维护（{@code isPublic=1}），用户也可以创建只对自己可见的动作（{@code isPublic=0}）。
 * 查询时按"公共 + 本人创建"过滤，天然实现可见性隔离。</p>
 */
@Entity
@Table(name = "exercise")
@Getter
@Setter
@NoArgsConstructor
public class Exercise {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 动作名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 主要肌群（胸/背/腿/肩/手臂/核心/有氧） */
    @Column(name = "muscle_group", length = 50)
    private String muscleGroup;

    /** 器械类型（杠铃/哑铃/器械/自重） */
    @Column(length = 50)
    private String equipment;

    /** 是否公共动作：1 公共 / 0 个人自定义 */
    @Column(name = "is_public", nullable = false)
    private Integer isPublic = 1;

    /** 创建人（自定义动作时填写，公共动作为 null） */
    @Column(name = "created_by")
    private Long createdBy;

    /** 状态：ACTIVE / DISABLED（停用后不可再被新计划引用，历史记录仍可读） */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

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

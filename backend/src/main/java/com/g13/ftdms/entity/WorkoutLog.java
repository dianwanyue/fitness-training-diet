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
 * 训练记录实体（表 {@code workout_log}），对应数据字典 D6。
 *
 * <p>{@code planId} 为空表示临时训练（用户没按计划也要能记录）。
 * {@code logDate} 是由 {@code performedAt} 按业务时区截断出的冗余列，
 * 目的是让"按日期区间统计"能走索引，而不必对时间戳做函数运算。</p>
 */
@Entity
@Table(name = "workout_log")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutLog {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 关联计划；null 表示临时训练 */
    @Column(name = "plan_id")
    private Long planId;

    /** 实际训练时间 */
    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    /** 训练归属日期（由 performedAt 按业务时区截断，冗余存储便于区间统计） */
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    /** 备注 */
    @Column(length = 500)
    private String note;

    /** 创建时间（记录一旦写入不再修改，故无 updated_at） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

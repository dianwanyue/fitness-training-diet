package com.g13.ftdms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 训练组明细实体（表 {@code workout_set}），对应数据字典 D7。
 *
 * <p>一行代表"某个动作的第 N 组"：实际重量、实际次数、是否完成。
 * 实际组数允许与计划的 {@code targetSets} 不同（用户可能多做或少做），
 * 统计容量时只计 {@code completed=1} 的组。</p>
 */
@Entity
@Table(name = "workout_set")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutSet {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属训练记录 */
    @Column(name = "log_id", nullable = false)
    private Long logId;

    /** 关联动作库 */
    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    /** 组序号（1–20，同一条记录内不重复） */
    @Column(name = "set_no", nullable = false)
    private Integer setNo = 1;

    /** 实际重量（kg），自重动作可为空 */
    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    /** 实际次数（1–100） */
    @Column
    private Integer reps;

    /** 该组是否完成：1 是 / 0 否 */
    @Column(nullable = false)
    private Integer completed = 1;
}

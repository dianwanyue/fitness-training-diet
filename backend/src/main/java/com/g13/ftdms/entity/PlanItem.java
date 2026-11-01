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
 * 计划动作实体（表 {@code plan_item}），对应数据字典 D5。
 *
 * <p>一个训练计划含 1–20 条明细。这里只存"计划要达到什么"（目标组数/次数/重量），
 * 实际完成情况记录在 {@link WorkoutSet}，两者互不覆盖——修改计划不会篡改历史训练记录。</p>
 */
@Entity
@Table(name = "plan_item")
@Getter
@Setter
@NoArgsConstructor
public class PlanItem {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属计划 */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 关联动作库 */
    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    /** 目标组数（1–20） */
    @Column(name = "target_sets", nullable = false)
    private Integer targetSets = 0;

    /** 目标每组次数（1–100） */
    @Column(name = "target_reps", nullable = false)
    private Integer targetReps = 0;

    /** 目标重量（kg），自重动作可为空 */
    @Column(name = "target_weight_kg", precision = 6, scale = 2)
    private BigDecimal targetWeightKg;

    /** 排序号（0–99），决定页面展示顺序 */
    @Column(name = "order_no", nullable = false)
    private Integer orderNo = 0;
}

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
import java.time.LocalDateTime;

/**
 * 食物库实体（表 {@code food}），对应数据字典 D8。
 *
 * <p>营养值统一按"每 100 g"标注，并强制登记 {@code source}（包装标签或资料名称）与
 * {@code state}（生重/熟重/品牌规格），这是本项目"营养数据可追溯"要求的落点。
 * 历史饮食记录保存的是当时的快照，因此本表修改不会影响已产生的汇总。</p>
 */
@Entity
@Table(name = "food")
@Getter
@Setter
@NoArgsConstructor
public class Food {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 食物名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 品牌或规格说明 */
    @Column(length = 100)
    private String brand;

    /** 每 100 g 能量（kcal），0–900 */
    @Column(name = "kcal_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal kcalPer100g = BigDecimal.ZERO;

    /** 每 100 g 蛋白质（g），0–100 */
    @Column(name = "protein_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinPer100g = BigDecimal.ZERO;

    /** 每 100 g 碳水化合物（g），0–100 */
    @Column(name = "carb_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbPer100g = BigDecimal.ZERO;

    /** 每 100 g 脂肪（g），0–100 */
    @Column(name = "fat_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatPer100g = BigDecimal.ZERO;

    /** 生熟状态：RAW 生重 / COOKED 熟重 / BRAND 品牌规格 */
    @Column(nullable = false, length = 16)
    private String state = "RAW";

    /** 营养数据来源（必填，如"中国食物成分表 第6版"或包装标签） */
    @Column(length = 255)
    private String source;

    /** 是否公共食物：1 公共 / 0 个人自定义 */
    @Column(name = "is_public", nullable = false)
    private Integer isPublic = 1;

    /** 创建人 */
    @Column(name = "created_by")
    private Long createdBy;

    /** 状态：ACTIVE / DISABLED */
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

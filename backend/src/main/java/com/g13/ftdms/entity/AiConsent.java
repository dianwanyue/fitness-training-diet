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
 * AI 分析授权实体（表 {@code ai_consent}），对应数据字典 D11。
 *
 * <p>数据层在本阶段即已就位：授权必须显式、可撤回、可追溯。
 * 接口（G1–G3）计划在 W7 实现，届时直接复用本实体与 {@code AiConsentRepository}，
 * 无需再改表结构。</p>
 */
@Entity
@Table(name = "ai_consent")
@Getter
@Setter
@NoArgsConstructor
public class AiConsent {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 授权范围：首版唯一取值 BODY_DIET_TRAINING_30D */
    @Column(nullable = false, length = 100)
    private String scope = "BODY_DIET_TRAINING_30D";

    /** 授权时间（服务端生成） */
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /** 撤回时间，null 表示未撤回 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** 状态：GRANTED / REVOKED */
    @Column(nullable = false, length = 16)
    private String status = "GRANTED";

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
        if (grantedAt == null) {
            grantedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

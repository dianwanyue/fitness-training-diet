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
 * 用户账户实体（表 {@code user_account}）。
 *
 * <p>对应数据字典 D1。口令只存 BCrypt 散列；{@code role} 区分普通用户与管理员，
 * {@code status} 控制账户是否可登录。所有私有数据表通过 {@code user_id} 与本表关联。</p>
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，全局唯一 */
    @Column(nullable = false, length = 50, unique = true)
    private String username;

    /** BCrypt 口令散列，禁止存明文 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** 角色：USER 普通用户 / ADMIN 管理员 */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    /** 昵称，缺省取用户名 */
    @Column(length = 50)
    private String nickname;

    /** 性别：MALE / FEMALE / UNKNOWN */
    @Column(length = 10)
    private String gender;

    /** 出生日期，仅用于 AI 摘要计算年龄 */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 身高（cm） */
    @Column(name = "height_cm", precision = 5, scale = 2)
    private java.math.BigDecimal heightCm;

    /** 账户状态：ACTIVE / DISABLED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 创建时间（由 @PrePersist 写入） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间（由 @PrePersist/@PreUpdate 写入） */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 插入前补齐时间戳，保证实体返回给前端时字段非空 */
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /** 更新前刷新 updatedAt */
    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 是否为管理员 */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

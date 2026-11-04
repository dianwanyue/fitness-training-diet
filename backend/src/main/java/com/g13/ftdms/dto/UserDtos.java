package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资料相关载体（接口族 A4/A5）。
 */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * A4/A5 用户资料响应。
     *
     * <p>只返回可公开字段，绝不包含 passwordHash；角色与状态供前端做按钮权限判断。</p>
     */
    public record ProfileResponse(
            Long id,
            String username,
            String nickname,
            String gender,
            LocalDate birthDate,
            BigDecimal heightCm,
            String role,
            String status,
            LocalDateTime createdAt
    ) {
    }

    /**
     * A5 修改资料请求。
     *
     * <p>语义：字段缺省（null）表示不修改，显式传空字符串表示清除。
     * 用户名、角色、状态不在可改范围内（由 Service 忽略）。</p>
     */
    public record UpdateProfileRequest(
            @Pattern(regexp = "^$|" + ValidationPatterns.NICKNAME, message = "昵称需为 1–50 字，首尾不得为空白（空字符串可清除）")
            String nickname,

            @Pattern(regexp = ValidationPatterns.GENDER, message = "性别取值需为 MALE / FEMALE / UNKNOWN")
            String gender,

            @Pattern(regexp = "^$|" + ValidationPatterns.DATE, message = "出生日期格式需为 yyyy-MM-dd（空字符串可清除）")
            String birthDate,

            @DecimalMin(value = "100", message = "身高(cm) 需在 100–250 之间")
            @DecimalMax(value = "250", message = "身高(cm) 需在 100–250 之间")
            @Digits(integer = 3, fraction = 2, message = "身高最多 2 位小数")
            BigDecimal heightCm
    ) {
    }

    /**
     * A5 修改资料响应。
     */
    public record UpdateProfileResponse(
            Long id,
            String nickname,
            String gender,
            LocalDate birthDate,
            BigDecimal heightCm
    ) {
    }
}

package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 身体数据相关载体（接口族 E1–E3）。
 */
public final class BodyDtos {

    private BodyDtos() {
    }

    /**
     * E1 记录/更新当日体重请求。
     *
     * <p>同一天重复提交按更新处理（数据库唯一约束保证只有一条），
     * 因此前端不需要区分"新增"和"修改"。</p>
     */
    public record BodyUpsertRequest(
            @NotBlank(message = "记录日期不能为空")
            @Pattern(regexp = ValidationPatterns.DATE, message = "记录日期格式需为 yyyy-MM-dd")
            String recordDate,

            @NotNull(message = "体重不能为空")
            @DecimalMin(value = "20", message = "体重(kg) 需在 20–300 之间")
            @DecimalMax(value = "300", message = "体重(kg) 需在 20–300 之间")
            @Digits(integer = 3, fraction = 2, message = "体重最多 2 位小数")
            BigDecimal weightKg,

            @Pattern(regexp = ValidationPatterns.NOTE, message = "备注不能超过 500 字")
            String note
    ) {
    }

    /** 体重记录项 */
    public record BodyItem(Long id, LocalDate recordDate, BigDecimal weightKg) {
    }

    /**
     * E2 体重列表响应。
     *
     * <p>{@code validDays} 是"区间内实际有记录的天数"，与 total 的区别在于
     * 后者是记录条数（因同日唯一，两者实际相等，分别保留以便口径说明）。</p>
     */
    public record BodyListResponse(List<BodyItem> list, int validDays, long total) {
    }
}

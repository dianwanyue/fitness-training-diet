package com.g13.ftdms.dto;

import com.g13.ftdms.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 后台管理相关载体（接口族 H5）。
 *
 * <p>H1–H4 的动作/食物维护请求复用 {@link CatalogDtos} 中的定义，
 * 本类只放账户状态变更这类独立语义的载体。</p>
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * H5 变更账户状态请求。
     *
     * <p>只允许 ACTIVE / DISABLED：没有"删除用户"接口，
     * 停用即保留历史数据、阻止登录，符合审计要求。</p>
     */
    public record UserStatusRequest(
            @NotBlank(message = "目标状态不能为空")
            @Pattern(regexp = ValidationPatterns.ACC_STATUS, message = "账户状态取值需为 ACTIVE 或 DISABLED")
            String status
    ) {
    }

    /** H5 变更结果 */
    public record UserStatusResponse(Long id, String status) {
    }
}

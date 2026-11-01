package com.g13.ftdms.controller;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.dto.BodyDtos;
import com.g13.ftdms.security.UserPrincipal;
import com.g13.ftdms.service.BodyService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 身体数据接口（接口族 E1–E3）。
 *
 * <p>写入使用 PUT 而非 POST：语义是"按日期 upsert"（同一天只会有一条），
 * 与 REST 中 PUT 的幂等语义一致。</p>
 */
@RestController
@RequestMapping("/api/v1/body-records")
public class BodyController {

    private final BodyService bodyService;

    public BodyController(BodyService bodyService) {
        this.bodyService = bodyService;
    }

    /**
     * E1 记录或更新当日体重。
     *
     * @param me      当前用户
     * @param request 日期、体重与备注
     * @return 写入后的记录
     */
    @PutMapping
    public ApiResponse<BodyDtos.BodyItem> upsert(@AuthenticationPrincipal UserPrincipal me,
                                                 @Valid @RequestBody BodyDtos.BodyUpsertRequest request) {
        return ApiResponse.ok(bodyService.upsert(me.id(), request));
    }

    /**
     * E2 查询体重记录。
     *
     * @param me   当前用户
     * @param from 起始日期
     * @param to   结束日期
     * @return 记录列表与有效天数
     */
    @GetMapping
    public ApiResponse<BodyDtos.BodyListResponse> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(bodyService.list(me.id(), from, to));
    }

    /**
     * E3 删除体重记录。
     *
     * @param me 当前用户
     * @param id 记录编号
     * @return 无业务数据
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        bodyService.delete(me.id(), id);
        return ApiResponse.ok();
    }
}

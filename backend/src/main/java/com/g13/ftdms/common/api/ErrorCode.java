package com.g13.ftdms.common.api;

/**
 * 业务错误码与默认提示语。
 *
 * <p>取值与《接口文档》V1.4 第 2.8 节「错误码一览」完全一致：
 * 0 成功；1xxx 参数与状态类；2xxx 认证类；4xxx 领域取值类；5xxx AI 类；9xxx 系统类。</p>
 *
 * <p>约定：错误码是**对外契约**，前端按码分支处理，不得随意增删；
 * 新增场景优先复用已有码，确需新增时须同步更新接口文档。</p>
 */
public enum ErrorCode {

    /** 成功 */
    OK(0, "OK"),

    /** 字段缺失、格式不符、超出值域 */
    PARAM_INVALID(1001, "参数校验失败"),
    /** 缺少 Authorization 头、令牌过期、令牌已登出 */
    UNAUTHORIZED(1002, "未认证或令牌失效"),
    /** 普通用户调用管理接口 */
    FORBIDDEN(1003, "权限不足"),
    /** 资源不存在，或存在但不属于当前用户（不区分，避免探测他人数据） */
    NOT_FOUND(1004, "资源不存在或不属于本人"),
    /** 计划已完成/已取消后再变更、重复变更 */
    STATE_CONFLICT(1005, "状态冲突"),
    /** 用户名重复、唯一约束冲突 */
    DUPLICATE(1006, "数据重复"),

    /** 账号不存在或口令错误（不区分，避免账号枚举） */
    BAD_CREDENTIALS(2001, "账号或口令错误"),
    /** 账户状态为 DISABLED */
    ACCOUNT_DISABLED(2002, "账号已停用"),

    /** 摄入克重不在 1–2000 g */
    GRAMS_OUT_OF_RANGE(4001, "摄入克重超出范围"),

    /** 未授权或已撤回 AI 分析授权 */
    AI_NOT_AUTHORIZED(5001, "未授权 AI 分析"),
    /** 模型服务不可用或超时（本阶段未实现，保留码位） */
    AI_UNAVAILABLE(5002, "模型服务不可用"),
    /** 分析区间内有效记录少于 3 天（本阶段未实现，保留码位） */
    AI_INSUFFICIENT_DATA(5003, "有效记录不足"),

    /** 未预期异常 */
    INTERNAL_ERROR(9000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

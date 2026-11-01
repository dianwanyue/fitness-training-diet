package com.g13.ftdms.common.exception;

import com.g13.ftdms.common.api.ErrorCode;

/**
 * 业务异常。
 *
 * <p>用途：Service 层校验不通过时抛出，由 {@link GlobalExceptionHandler} 统一转成
 * {@code {code, message}} 响应。与受检异常不同，这里继承 RuntimeException，
 * 目的是不污染方法签名，同时保证事务按默认规则回滚。</p>
 *
 * <p>约定：凡是"用户可理解、可自助修正"的问题（参数非法、资源不存在、状态冲突）都用本异常；
 * 数据库连不上、空指针等系统级问题交给兜底处理（返回 9000 并记 error 日志）。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final ErrorCode errorCode;

    /**
     * @param errorCode 错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 错误码
     * @param message   面向用户的具体提示（如"体重(kg) 需在 20–300 之间"）
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 参数校验失败的快捷构造 */
    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    /** 资源不存在（或不属于当前用户）的快捷构造 */
    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }
}

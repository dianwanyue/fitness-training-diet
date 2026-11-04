package com.g13.ftdms.common.exception;

import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>职责：把 Controller/Service 抛出的各类异常翻译成统一响应体，保证前端只需处理一种结构。
 * 处理顺序遵循"具体优先"，由 Spring 按异常类型的最近匹配选择方法。</p>
 *
 * <p>日志策略：4xx 语义的业务异常打 warn（含 traceId 便于串联），系统异常打 error 并带堆栈；
 * 参数校验失败只记字段摘要，不打印整个请求体，避免口令等敏感字段落盘。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：Service 主动抛出，按声明的错误码返回 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException ex, HttpServletRequest request) {
        log.warn("业务异常 code={} path={} msg={}", ex.getErrorCode().getCode(), request.getRequestURI(), ex.getMessage());
        return ApiResponse.fail(ex.getErrorCode(), ex.getMessage());
    }

    /** 请求体字段校验失败（@Valid 触发）：拼出「字段: 提示」摘要，前端可直接展示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .distinct()
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 path={} detail={}", request.getRequestURI(), detail);
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail.isEmpty() ? null : detail);
    }

    /** 表单/查询参数绑定失败（@ModelAttribute） */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException ex, HttpServletRequest request) {
        String detail = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .distinct()
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败 path={} detail={}", request.getRequestURI(), detail);
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail.isEmpty() ? null : detail);
    }

    /** 方法参数级校验失败（@RequestParam 上的 @Min/@Max 等） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数约束失败 path={} detail={}", request.getRequestURI(), detail);
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail.isEmpty() ? null : detail);
    }

    /** JSON 解析失败：字段类型不匹配、日期格式非法、请求体为空等 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("请求体解析失败 path={} msg={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求体格式不正确，请检查字段类型与日期格式（yyyy-MM-dd）");
    }

    /** 必填查询参数缺失 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "缺少必填参数：" + ex.getParameterName());
    }

    /** 查询参数类型不匹配（如 id 传了字母） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "参数 " + ex.getName() + " 格式不正确");
    }

    /** 无权限：普通用户访问 /admin 接口 */
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("越权访问 path={}", request.getRequestURI());
        return ApiResponse.fail(ErrorCode.FORBIDDEN, null);
    }

    /** 唯一约束等数据库完整性冲突：如并发注册同名用户 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("数据完整性冲突 path={} msg={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ApiResponse.fail(ErrorCode.DUPLICATE, "数据已存在或违反唯一性约束");
    }

    /** 路径不存在 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<Void> handleNoHandler(NoHandlerFoundException ex) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND, "接口不存在：" + ex.getRequestURL());
    }

    /** Spring 6.1+ 静态资源兜底产生的缺失路由，与无 Handler 使用同一业务码。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException ex) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND, "接口不存在：" + ex.getResourcePath());
    }

    /** 方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求方法不支持：" + ex.getMethod());
    }

    /** 兜底：未预期异常统一返回 9000，并打印堆栈供排查 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOthers(Exception ex, HttpServletRequest request) {
        log.error("未预期异常 path={}", request.getRequestURI(), ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, null);
    }

    /** 把 FieldError 转成「中文提示」；有 defaultMessage 时优先使用 */
    private String formatFieldError(FieldError error) {
        String msg = error.getDefaultMessage();
        return (msg == null || msg.isBlank()) ? error.getField() + " 不合法" : msg;
    }
}

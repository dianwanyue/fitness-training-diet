package com.g13.ftdms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.g13.ftdms.common.web.TraceIdFilter;

/**
 * 统一响应包装体（对应《接口文档》第 2 章「统一返回结构」）。
 *
 * <p>JSON 形态：{@code {"code":0,"message":"OK","data":{...},"timestamp":1730000000000,"traceId":"a1b2c3d4e5f6"}}</p>
 *
 * <p>设计意图：
 * <ul>
 *   <li>{@code data} 强制输出（含 null），前端无需区分「字段缺失」与「值为空」；</li>
 *   <li>{@code traceId} 取自 MDC，由 {@link TraceIdFilter} 在请求入口生成，
 *       出现 9000 时把它报给服务端即可定位同一次请求的全部日志；</li>
 *   <li>业务成功与业务失败都返回 HTTP 200，由 code 区分——避免代理/网关改写错误响应体。</li>
 * </ul></p>
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    /** 业务码：0 成功，其余见 {@link ErrorCode} */
    private int code;

    /** 提示信息：失败时为中文可读文案 */
    private String message;

    /** 业务数据：无数据时为 null */
    private T data;

    /** 服务端时间戳（毫秒） */
    private long timestamp;

    /** 链路标识：12 位十六进制，与日志中的 traceId 一致 */
    private String traceId;

    public ApiResponse() {
        // Jackson 反序列化需要无参构造
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.traceId = TraceIdFilter.currentTraceId();
    }

    /**
     * 构造成功响应。
     *
     * @param data 业务数据，可为 null（表示无内容）
     * @param <T>  数据类型
     * @return code=0 的响应体
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    /**
     * 构造成功但无数据的响应（如退出登录、删除记录）。
     *
     * @return data 为 null 的响应体
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), null);
    }

    /**
     * 构造失败响应。
     *
     * @param errorCode 错误码枚举
     * @param message   覆盖默认提示语；为 null 时使用枚举默认文案
     * @param <T>       数据类型（失败时恒为 null）
     * @return 非 0 业务码的响应体
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        String msg = (message == null || message.isBlank()) ? errorCode.getMessage() : message;
        return new ApiResponse<>(errorCode.getCode(), msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}

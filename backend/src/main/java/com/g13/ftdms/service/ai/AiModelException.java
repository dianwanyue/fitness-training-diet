package com.g13.ftdms.service.ai;

/**
 * 模型调用失败（连接失败、超时、非 2xx、响应无法解析）。
 *
 * <p>该异常不会冒泡到接口层：{@code AiService} 捕获后把报告置为
 * {@code DEGRADED}，保留统计摘要并提示稍后重试，符合 FR-29 的降级口径。</p>
 */
public class AiModelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiModelException(String message) {
        super(message);
    }

    public AiModelException(String message, Throwable cause) {
        super(message, cause);
    }
}

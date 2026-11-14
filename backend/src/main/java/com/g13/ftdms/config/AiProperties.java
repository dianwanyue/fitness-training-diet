package com.g13.ftdms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 分析模块配置（前缀 {@code app.ai}）。
 *
 * <p>模型服务固定为服务端本地部署的 Ollama，不向公网发送数据。
 * 所有参数均可由环境变量覆盖，便于在演示服务器上调整模型与超时：</p>
 *
 * <ul>
 *   <li>{@code OLLAMA_BASE_URL}：Ollama 服务地址，默认 {@code http://127.0.0.1:11434}；</li>
 *   <li>{@code OLLAMA_MODEL}：模型标识，默认 {@code qwen3:4b-instruct-2507-q4_K_M}；</li>
 *   <li>{@code AI_TIMEOUT_SECONDS}：单次生成超时，默认 60 秒，超时按 DEGRADED 降级；</li>
 *   <li>{@code AI_ENABLED}：置为 false 时不发起模型调用，直接走降级路径。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AiProperties {

    /** 是否允许调用模型；关闭时 G4 仍返回统计值，但状态为 DEGRADED */
    private boolean enabled = true;

    /** Ollama 服务基址（不带结尾斜杠） */
    private String baseUrl = "http://127.0.0.1:11434";

    /** 模型标识，随报告入库，便于追溯 */
    private String model = "qwen3:4b-instruct-2507-q4_K_M";

    /** 模型展示名称 */
    private String modelName = "Qwen3-4B-Instruct-2507";

    /** 读取超时（秒） */
    private int timeoutSeconds = 60;

    /** 建立连接超时（秒），避免模型进程未启动时长时间等待 */
    private int connectTimeoutSeconds = 3;

    /** 输入摘要序列化后的字节上限（与 SRS DS15 的 ≤8 KB 一致） */
    private int maxSummaryBytes = 8192;

    /** 模型输出正文的字符上限（与 ai_report.content 的数据字典一致） */
    private int maxContentChars = 4096;

    /** 生成报告所需的最小有效记录天数，低于该值返回 5003 */
    private int minValidDays = 3;

    /** 采样温度：分析类文本取低值，减少发挥与数字漂移 */
    private double temperature = 0.2;

    /** 最大生成 token 数，限制 CPU 推理耗时 */
    private int numPredict = 900;
}

package com.g13.ftdms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g13.ftdms.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ollama 模型客户端。
 *
 * <p>使用 JDK 自带 {@link HttpClient}，不额外引入 HTTP 客户端依赖。
 * 只调用本地 {@code /api/chat}，输入为「系统提示 + 摘要 JSON」两条消息，
 * 输出取 {@code message.content}（兼容 {@code /api/generate} 的 {@code response} 字段）。</p>
 *
 * <p>超时策略：连接超时 3 秒（模型进程未启动时快速失败），读取超时默认 60 秒。
 * 任一失败都抛 {@link AiModelException}，由上层降级，不影响统计接口。</p>
 */
@Component
public class AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClient.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiModelClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(properties.getConnectTimeoutSeconds(), 1)))
                .build();
    }

    /**
     * 调用模型生成分析文本。
     *
     * @param systemPrompt 固定系统提示（角色、边界、输出格式）
     * @param userPrompt   用户提示（含摘要 JSON）
     * @return 模型输出正文与模型标识
     * @throws AiModelException 服务不可用、超时、非 2xx 或响应为空
     */
    public Result chat(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new AiModelException("AI 模型调用已关闭（app.ai.enabled=false）");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        ObjectNode options = body.putObject("options");
        options.put("temperature", properties.getTemperature());
        options.put("num_predict", properties.getNumPredict());

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 1)))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException | IllegalArgumentException ex) {
            throw new AiModelException("模型请求构建失败：" + ex.getMessage(), ex);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiModelException("模型调用被中断", ex);
        } catch (IOException ex) {
            throw new AiModelException("模型服务不可达：" + ex.getMessage(), ex);
        }

        if (response.statusCode() / 100 != 2) {
            throw new AiModelException("模型服务返回 HTTP " + response.statusCode());
        }

        try {
            JsonNode node = objectMapper.readTree(response.body());
            String content = node.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                content = node.path("response").asText(null);
            }
            if (content == null || content.isBlank()) {
                throw new AiModelException("模型响应没有正文");
            }
            String model = node.path("model").asText(properties.getModel());
            log.info("模型调用成功 model={} chars={}", model, content.length());
            return new Result(content, model);
        } catch (IOException ex) {
            throw new AiModelException("模型响应无法解析", ex);
        }
    }

    /** 去掉配置中可能出现的结尾斜杠，避免拼出双斜杠 */
    private String baseUrl() {
        String url = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 模型调用结果。
     *
     * @param content 正文
     * @param model   实际模型标识（Ollama 返回值，缺失时回退配置值）
     */
    public record Result(String content, String model) {
    }
}

package com.g13.ftdms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g13.ftdms.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型客户端测试：用 JDK 内置 HttpServer 模拟 Ollama，
 * 覆盖成功、非 2xx 与关闭开关三种路径，不依赖真实模型进程。
 */
class AiModelClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesChatResponseAndSendsConfiguredModel() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(200, "{\"model\":\"qwen3:4b-test\",\"message\":{\"role\":\"assistant\",\"content\":\"【数据概况】测试正文\"}}",
                requestBody);

        AiProperties properties = properties();
        AiModelClient client = new AiModelClient(properties, new ObjectMapper());
        AiModelClient.Result result = client.chat("system", "user");

        assertEquals("【数据概况】测试正文", result.content());
        assertEquals("qwen3:4b-test", result.model());
        JsonNode sent = new ObjectMapper().readTree(requestBody.get());
        assertEquals("qwen3:4b-test", sent.path("model").asText());
        assertFalse(sent.path("stream").asBoolean());
        assertEquals("system", sent.path("messages").get(0).path("content").asText());
    }

    @Test
    void failsFastOnNon2xx() throws IOException {
        startServer(500, "{\"error\":\"boom\"}", new AtomicReference<>());
        AiModelClient client = new AiModelClient(properties(), new ObjectMapper());
        AiModelException ex = assertThrows(AiModelException.class, () -> client.chat("s", "u"));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void reportsFailureWhenDisabled() {
        AiProperties properties = properties();
        properties.setEnabled(false);
        AiModelClient client = new AiModelClient(properties, new ObjectMapper());
        assertThrows(AiModelException.class, () -> client.chat("s", "u"));
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        int port = server == null ? 1 : server.getAddress().getPort();
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setModel("qwen3:4b-test");
        properties.setTimeoutSeconds(5);
        properties.setConnectTimeoutSeconds(2);
        return properties;
    }

    private void startServer(int status, String body, AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }
}

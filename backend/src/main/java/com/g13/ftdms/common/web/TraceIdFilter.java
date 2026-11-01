package com.g13.ftdms.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * 请求链路标识过滤器。
 *
 * <p>作用：为每个请求生成 12 位十六进制 traceId，写入日志上下文（MDC）与响应头
 * {@code X-Trace-Id}，供 {@code ApiResponse} 回填、供排查问题时串联日志。</p>
 *
 * <p>实现意图：traceId 必须"一次请求一个"，因此继承 {@link OncePerRequestFilter}；
 * 过滤器链抛出异常时也要清理 MDC，避免线程池复用时把旧 traceId 带给下一个请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 日志与响应头统一使用的键名 */
    public static final String TRACE_ID_KEY = "traceId";
    /** 响应头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程复用场景下必须清理，否则下个请求会继承旧值
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 读取当前线程的 traceId。
     *
     * @return 12 位十六进制字符串；不在请求线程内时返回固定占位值（如定时任务）
     */
    public static String currentTraceId() {
        String id = MDC.get(TRACE_ID_KEY);
        return id != null ? id : "000000000000";
    }

    /** 生成 6 字节随机数的十六进制表示（12 位），碰撞概率足够低且长度固定 */
    private static String generateTraceId() {
        byte[] buf = new byte[6];
        RANDOM.nextBytes(buf);
        char[] out = new char[12];
        for (int i = 0; i < 6; i++) {
            out[i * 2] = HEX[(buf[i] >> 4) & 0x0F];
            out[i * 2 + 1] = HEX[buf[i] & 0x0F];
        }
        return new String(out);
    }
}

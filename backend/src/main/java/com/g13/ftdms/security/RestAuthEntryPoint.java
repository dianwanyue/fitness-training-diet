package com.g13.ftdms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g13.ftdms.common.api.ApiResponse;
import com.g13.ftdms.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证/授权失败的统一出口。
 *
 * <p>为什么需要它：Spring Security 的失败发生在进入 Controller 之前，
 * {@code @RestControllerAdvice} 捕获不到，若不单独处理会返回 HTML 或空响应体，
 * 前端拿不到 {code,message} 结构。这里分别实现两个接口并复用 {@link ApiResponse}，
 * 保证"所有响应都是同一种 JSON"。</p>
 *
 * <p>HTTP 状态码：认证失败 401、权限不足 403；业务码分别为 1002 / 1003。</p>
 */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 未认证或令牌失效 → 401 + 1002 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未认证或令牌失效");
    }

    /** 已认证但角色不足 → 403 + 1003 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "权限不足");
    }

    /**
     * 输出统一响应体。
     *
     * @param response 响应对象
     * @param httpStatus HTTP 状态码
     * @param errorCode  业务错误码
     * @param message    提示语
     */
    private void write(HttpServletResponse response, int httpStatus, ErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

package com.monitordata.fusion.platform;

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
import java.util.UUID;

/**
 * 为每个请求分配 requestId，写入响应头与日志上下文，供响应体与审计复用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = isSafe(incoming) ? incoming : UUID.randomUUID().toString();
        CURRENT.set(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            CURRENT.remove();
        }
    }

    private static boolean isSafe(String value) {
        return value != null && !value.isBlank() && value.length() <= 256 && value.matches("[A-Za-z0-9._:-]+");
    }

    public static String currentRequestId() {
        String id = CURRENT.get();
        return id != null ? id : UUID.randomUUID().toString();
    }
}

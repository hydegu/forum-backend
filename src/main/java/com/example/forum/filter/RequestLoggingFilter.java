package com.example.forum.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求日志过滤器，支持 MDC 分布式追踪
 *
 * 此过滤器功能：
 * - 为每个请求生成唯一的 traceId
 * - 记录请求/响应详细信息
 * - 填充 MDC 上下文用于结构化日志
 * - 测量请求处理时间
 */
@Slf4j
@Component
@Order(1) // 在安全过滤器之前执行
public class RequestLoggingFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";
    private static final String REQUEST_METHOD = "requestMethod";
    private static final String REQUEST_URI = "requestUri";
    private static final String CLIENT_IP = "clientIp";
    private static final String REQUEST_START_TIME = "requestStartTime";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 生成唯一追踪 ID
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID, traceId);

        // 捕获请求详情
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String clientIp = getClientIp(httpRequest);

        MDC.put(REQUEST_METHOD, method);
        MDC.put(REQUEST_URI, uri);
        MDC.put(CLIENT_IP, clientIp);

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        MDC.put(REQUEST_START_TIME, String.valueOf(startTime));

        // 包装请求/响应以缓存内容
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        // 记录进入请求
        log.info("收到请求: {} {} 来自 {}", method, uri, clientIp);

        try {
            // 继续过滤器链
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // 计算处理时间
            long duration = System.currentTimeMillis() - startTime;
            int status = wrappedResponse.getStatus();

            // 记录响应
            log.info("请求完成: {} {} - 状态: {} - 耗时: {}ms",
                    method, uri, status, duration);

            // 复制响应内容
            wrappedResponse.copyBodyToResponse();

            // 清理 MDC
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
            MDC.remove(REQUEST_METHOD);
            MDC.remove(REQUEST_URI);
            MDC.remove(CLIENT_IP);
            MDC.remove(REQUEST_START_TIME);
        }
    }

    /**
     * 提取客户端 IP 地址，考虑代理头
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理 X-Forwarded-For 中的多个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("请求日志过滤器已初始化");
    }

    @Override
    public void destroy() {
        log.info("请求日志过滤器已销毁");
    }
}
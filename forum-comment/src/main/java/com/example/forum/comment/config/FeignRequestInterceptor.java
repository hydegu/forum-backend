package com.example.forum.comment.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * Feign 请求拦截器
 * 用于在服务间调用时传递 JWT token
 */
@Slf4j
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Value("${com.jwt.user-token-name:authentication}")
    private String jwtTokenName;

    @Override
    public void apply(RequestTemplate template) {
        try {
            // 获取当前请求
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                log.debug("无法获取当前请求，跳过token传递");
                return;
            }

            HttpServletRequest request = attributes.getRequest();

            // 1. 尝试从请求头获取token
            String token = extractTokenFromHeader(request);
            
            // 2. 如果请求头没有，从Cookie获取
            if (token == null) {
                token = extractTokenFromCookie(request);
            }

            // 3. 将token添加到Feign请求头
            if (StringUtils.hasText(token)) {
                // 使用 Authorization 头传递（Bearer格式）
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                log.debug("已将JWT token添加到Feign请求头");
            } else {
                log.debug("当前请求中没有JWT token，跳过token传递");
            }

        } catch (Exception e) {
            log.warn("Feign请求拦截器处理失败: {}", e.getMessage());
            // 不抛出异常，允许请求继续
        }
    }

    /**
     * 从请求头中提取JWT token
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            // 尝试使用配置的token名称
            header = request.getHeader(jwtTokenName);
        }
        
        if (!StringUtils.hasText(header)) {
            return null;
        }
        
        // 支持 "Bearer token" 格式
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7);
        }
        
        return header;
    }

    /**
     * 从Cookie中提取JWT token
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        
        return Arrays.stream(request.getCookies())
                .filter(cookie -> jwtTokenName.equals(cookie.getName()))
                .map(cookie -> cookie.getValue())
                .findFirst()
                .orElse(null);
    }
}


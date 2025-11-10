package com.example.forum.filter;

import com.example.forum.config.JwtProperties;
import com.example.forum.util.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT网关全局过滤器
 * 在网关层统一验证JWT token，提供第一道安全防线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtProperties jwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 检查是否在白名单中
        if (isWhitelisted(path)) {
            log.debug("路径 {} 在白名单中，跳过JWT验证", path);
            return chain.filter(exchange);
        }

        // 2. 提取JWT token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("请求路径 {} 缺少JWT token", path);
            return unauthorized(exchange.getResponse(), "缺少认证信息");
        }

        // 3. 验证JWT token
        try {
            Claims claims = JwtUtils.parseJWT(jwtProperties.getUserSecretKey(), token);
            
            // 4. 提取用户信息
            String username = claims.get("username", String.class);
            Object userIdObj = claims.get("userId");
            
            if (username == null) {
                log.warn("JWT token中缺少用户名信息");
                return unauthorized(exchange.getResponse(), "无效的认证信息");
            }

            // 5. 将用户信息添加到请求头，传递给下游微服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userIdObj != null ? userIdObj.toString() : "")
                    .header("X-Username", username)
                    .build();

            log.debug("JWT验证成功，用户: {}, 路径: {}", username, path);

            // 6. 继续传递请求（JWT也会传递给微服务，实现双重验证）
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT验证失败: {}, 路径: {}", e.getMessage(), path);
            return unauthorized(exchange.getResponse(), "认证失败");
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        List<String> whitelist = jwtProperties.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }

        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从请求中提取JWT token
     * 优先从Authorization头提取，其次从自定义头，最后从Cookie
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. 从Authorization头提取（Bearer格式）
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7);
        }

        // 2. 从自定义token头提取
        String tokenName = jwtProperties.getUserTokenName();
        if (StringUtils.hasText(tokenName)) {
            String customHeader = request.getHeaders().getFirst(tokenName);
            if (StringUtils.hasText(customHeader)) {
                // 支持Bearer格式
                if (customHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    return customHeader.substring(7);
                }
                return customHeader;
            }
        }

        // 3. 从Cookie提取
        if (StringUtils.hasText(tokenName)) {
            List<String> cookies = request.getHeaders().get(HttpHeaders.COOKIE);
            if (cookies != null) {
                for (String cookie : cookies) {
                    String[] pairs = cookie.split(";");
                    for (String pair : pairs) {
                        String[] keyValue = pair.trim().split("=", 2);
                        if (keyValue.length == 2 && tokenName.equals(keyValue[0])) {
                            return keyValue[1];
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> result = new HashMap<>();
        result.put("code", HttpStatus.UNAUTHORIZED.value());
        result.put("message", message);

        try {
            byte[] bytes = objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("生成响应JSON失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 设置较高优先级，在其他过滤器之前执行
        return -100;
    }
}

package com.example.forum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Gateway CORS 配置
 * 解决前端跨域访问问题
 */
@Configuration
public class GatewayCorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        
        // 允许的源（前端地址）
        corsConfiguration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
        
        // 允许的HTTP方法
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // 允许的请求头
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));
        
        // 允许暴露的响应头
        corsConfiguration.setExposedHeaders(Arrays.asList("*"));
        
        // 允许携带凭证（Cookie、Authorization等）
        corsConfiguration.setAllowCredentials(true);
        
        // 预检请求的缓存时间（秒）
        corsConfiguration.setMaxAge(3600L);
        
        // 注册CORS配置到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        
        return new CorsWebFilter(source);
    }
}


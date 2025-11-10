package com.example.forum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT配置属性
 * 从Nacos配置中心读取JWT相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "com.jwt")
public class JwtProperties {

    /**
     * 用户端访问令牌密钥
     */
    private String userSecretKey;

    /**
     * 用户端令牌名称（用于从请求头或Cookie读取）
     */
    private String userTokenName = "authentication";

    /**
     * JWT白名单路径（不需要验证JWT的路径）
     * 支持Ant风格路径匹配: *, **, ?
     */
    private List<String> whitelist = new ArrayList<>();
}

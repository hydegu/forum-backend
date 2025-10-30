package com.example.forum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 上传配置参数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * 文件存储根目录。
     */
    private String directory = "uploads";

    /**
     * 对外访问的基础 URL 前缀。
     */
    private String baseUrl = "/uploads";
}

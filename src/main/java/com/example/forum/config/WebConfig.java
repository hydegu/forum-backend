package com.example.forum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    /**
     * Web 响应专用的 ObjectMapper
     * 标记为 @Primary，确保 Spring MVC 使用这个 ObjectMapper
     * 不包含类型信息，返回干净的 JSON 给前端
     */
    @Bean
    @Primary
    public ObjectMapper webObjectMapper() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .build();
        // 禁用时间戳格式，使用 ISO-8601 格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 不使用 activateDefaultTyping，确保返回干净的 JSON
        log.info("配置 Web ObjectMapper（不包含类型信息）");
        return mapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String baseUrl = uploadProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "/uploads";
        }
        if (!baseUrl.startsWith("/")) {
            baseUrl = "/" + baseUrl;
        }
        String handlerPattern = baseUrl.endsWith("/") ? baseUrl + "**" : baseUrl + "/**";

        Path directoryPath = Paths.get(uploadProperties.getDirectory());
        if (!directoryPath.isAbsolute()) {
            directoryPath = Paths.get(System.getProperty("user.dir")).resolve(directoryPath).normalize();
        }
        String location = directoryPath.toUri().toString();
        log.debug("配置静态资源映射：handler={} -> location={}", handlerPattern, location);
        registry.addResourceHandler(handlerPattern)
                .addResourceLocations(location);
    }
}

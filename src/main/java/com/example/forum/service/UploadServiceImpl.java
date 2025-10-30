package com.example.forum.service;

import com.example.forum.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final UploadProperties uploadProperties;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            Path uploadRoot = resolveUploadDir();
            Files.createDirectories(uploadRoot);

            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "");
            if (StringUtils.hasText(extension)) {
                filename = filename + "." + extension;
            }
            Path target = uploadRoot.resolve(filename);
            file.transferTo(target.toFile());

            String baseUrl = normalizeBaseUrl(uploadProperties.getBaseUrl());
            if (baseUrl.startsWith("http")) {
                return baseUrl.endsWith("/") ? baseUrl + filename : baseUrl + "/" + filename;
            }
            return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/" + filename;
        } catch (IOException ex) {
            log.error("文件上传失败", ex);
            throw new IllegalStateException("文件上传失败", ex);
        }
    }

    private Path resolveUploadDir() {
        String directory = uploadProperties.getDirectory();
        if (!StringUtils.hasText(directory)) {
            directory = "uploads";
        }
        Path path = Paths.get(directory);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        return path;
    }

    private String normalizeBaseUrl(String configuredBaseUrl) {
        String baseUrl = configuredBaseUrl;
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "/uploads";
        }
        baseUrl = baseUrl.trim();
        if (baseUrl.startsWith("http")) {
            return baseUrl;
        }
        if (!baseUrl.startsWith("/")) {
            baseUrl = "/" + baseUrl;
        }
        return baseUrl;
    }
}

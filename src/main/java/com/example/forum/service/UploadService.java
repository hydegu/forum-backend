package com.example.forum.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface UploadService {

    /**
     * 保存上传文件并返回可访问的 URL。
     */
    String store(MultipartFile file);
}

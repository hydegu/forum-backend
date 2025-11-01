package com.example.forum.user.controller;

import com.example.forum.user.entity.AppUser;
import com.example.forum.user.service.UploadService;
import com.example.forum.user.service.UserService;
import com.example.forum.user.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadService uploadService;
    private final UserService userService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        AppUser currentUser = SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .filter(user -> user != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
        String url = uploadService.store(file);
        log.info("用户 {} 上传文件成功，url={}", currentUser.getUserName(), url);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}

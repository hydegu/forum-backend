package com.example.forum.user.config;

import com.example.forum.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserGlobalExceptionHandler extends GlobalExceptionHandler {
    // 继承公共异常处理器
}

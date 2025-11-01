package com.example.forum.common.exception;

import com.example.forum.common.enums.Code;
import com.example.forum.common.dto.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Code.BAD_REQUEST, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数校验失败");
        log.warn("约束校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Code.BAD_REQUEST, message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Object>> handleApiException(ApiException ex) {
        HttpStatus status = ex.getStatus();
        log.warn("业务异常: status={}, message={}", status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(Result.error(convertStatusToCode(status), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOtherException(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(Code.INTERNAL_SERVER_ERROR, "服务器内部错误"));
    }

    private Code convertStatusToCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> Code.BAD_REQUEST;
            case UNAUTHORIZED -> Code.UNAUTHORIZED;
            case FORBIDDEN -> Code.FORBIDDEN;
            case NOT_FOUND -> Code.NOT_FOUND;
            case GONE -> Code.GONE;
            case CONFLICT -> Code.CONFLICT;
            default -> Code.INTERNAL_SERVER_ERROR;
        };
    }
}

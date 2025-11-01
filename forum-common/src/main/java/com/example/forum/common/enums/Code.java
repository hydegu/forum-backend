package com.example.forum.common.enums;

public enum Code {
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求无效"),
    UNAUTHORIZED(401, "未经授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "未找到资源"),
    GONE(410, "资源已失效"),
    CONFLICT(409, "请求冲突"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    TOO_MANY_REQUESTS(429,"过于频繁");

    private final int code;
    private final String message;

    // 构造函数
    Code(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // 获取状态码
    public int getCode() {
        return code;
    }

    // 获取状态消息
    public String getMessage() {
        return message;
    }
}

package com.example.forum.dto;

import com.example.forum.enums.Code;

public class Result<T> {

    private int code;        // 状态码
    private String message;   // 描述信息
    private T result;        // 返回的结果

    // 默认构造函数
    public Result() {}

    // 构造函数
    public Result(Code code, T result) {
        this.code = code.getCode();
        this.message = code.getMessage();
        this.result = result;
    }

    // 构造函数，传递状态码和消息
    public Result(int code, String message, T result) {
        this.code = code;
        this.message = message;
        this.result = result;
    }

    // 返回成功的结果
    public static <T> Result<T> success(T result) {
        return new Result<>(Code.SUCCESS, result);
    }

    // 返回失败的结果
    public static <T> Result<T> error(Code code) {
        return new Result<>(code, null);
    }

    // 返回失败的结果，自定义错误信息
    public static <T> Result<T> error(Code code, String message) {
        return new Result<>(code.getCode(), message, null);
    }

    // Getter and Setter methods

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}



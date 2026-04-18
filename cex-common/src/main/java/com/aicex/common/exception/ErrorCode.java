package com.aicex.common.exception;

public enum ErrorCode {
    BAD_REQUEST(40000, "请求参数错误"),
    BUSINESS_ERROR(50001, "业务处理失败"),
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}

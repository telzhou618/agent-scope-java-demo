package com.example.agent.error;

/**
 * 业务异常：带错误码与可读消息，由 GlobalExceptionHandler 统一转成 Result
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

package com.smartparking.common.core.exception;

import lombok.Getter;

/**
 * 业务异常：携带错误码，供全局异常处理器统一转换为 Result.fail(code, message)
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(com.smartparking.common.core.exception.ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(com.smartparking.common.core.exception.ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}

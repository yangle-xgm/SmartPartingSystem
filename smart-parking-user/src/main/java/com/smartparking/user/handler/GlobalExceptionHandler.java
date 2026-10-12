package com.smartparking.user.handler;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        return Result.fail(detail);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        return Result.fail("系统内部错误，请联系管理员");
    }
}

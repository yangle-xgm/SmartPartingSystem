package com.smartparking.user.handler;

import com.smartparking.common.core.handler.BaseGlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一实现见 common-core 的 BaseGlobalExceptionHandler
 * （前端功能测试 DEF-02：约束类异常映射成人话，未处理异常不再回显 SQL 与堆栈）
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {
}

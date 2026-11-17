package com.smartparking.common.core.handler;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.time.format.DateTimeParseException;

/**
 * 全局异常处理基类（各服务继承即可，避免 8 份重复实现各自漂移）。
 *
 * <p>前端功能测试 DEF-02：原实现把 {@code e.getMessage()} 原样返回，重复车位编号时
 * 用户能看到完整 MyBatis 异常栈与 {@code ### SQL: INSERT INTO parking_space ...}，
 * 泄漏表名/列名/索引名。这里统一改为：业务异常用错误码文案，约束类异常映射成人话，
 * 其余异常只返回通用提示（堆栈只进日志）。</p>
 */
@Slf4j
public abstract class BaseGlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 唯一索引冲突（如车位编号、车牌号重复）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        String hint = resolveDuplicateHint(e);
        log.warn("唯一约束冲突: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.DATA_DUPLICATE, hint);
    }

    /**
     * 其他数据完整性异常（字段超长、数值越界、非空约束等）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性异常: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.DATA_INVALID);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return Result.fail(ErrorCode.DATA_INVALID, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        return Result.fail(ErrorCode.DATA_INVALID, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.DATA_INVALID.getMessage());
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ErrorCode.DATA_INVALID, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "参数格式不正确：" + e.getName());
    }

    /**
     * 手工 LocalDate.parse 失败（如 startDate=not-a-date）
     */
    @ExceptionHandler(DateTimeParseException.class)
    public Result<Void> handleDateTimeParse(DateTimeParseException e) {
        log.warn("日期格式错误: {}", e.getParsedString());
        return Result.fail(ErrorCode.PARAM_INVALID, "日期格式不正确，应为 yyyy-MM-dd");
    }

    /**
     * 路径不存在（Spring 6.1 静态资源兜底抛出的 NoResourceFoundException）
     *
     * <p>不单独拦截时会落进 Runtime/Exception 兜底变成 500「系统内部错误」，
     * 让调用方以为服务炸了；这里按语义回 404。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求路径不存在: {} {}", e.getHttpMethod(), e.getResourcePath());
        return Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /**
     * 兜底：只记录日志，不把内部细节（SQL、包名、堆栈）返回给调用方
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("未处理异常：{}", e.getMessage(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    private String firstFieldError(FieldError fieldError) {
        if (fieldError == null) {
            return ErrorCode.DATA_INVALID.getMessage();
        }
        return fieldError.getDefaultMessage();
    }

    /**
     * 把索引名翻译成用户能看懂的提示，未命中则用通用文案
     */
    private String resolveDuplicateHint(DuplicateKeyException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();
        if (msg.contains("uk_space_no")) {
            return "车位编号已存在，请换一个编号";
        }
        if (msg.contains("uk_plate_no")) {
            return "该车牌已登记，请勿重复绑定";
        }
        if (msg.contains("uk_username") || msg.contains("username")) {
            return ErrorCode.USERNAME_EXISTS.getMessage();
        }
        if (msg.contains("uk_invite_code") || msg.contains("invite_code")) {
            return "邀请码重复，请重试";
        }
        return ErrorCode.DATA_DUPLICATE.getMessage();
    }
}

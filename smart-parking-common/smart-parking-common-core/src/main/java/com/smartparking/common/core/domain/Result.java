package com.smartparking.common.core.domain;

import com.smartparking.common.core.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 业务错误码失败（E2E 报告 P2-1：原 fail(String) 一律 code=500，
     * 前端 CODE_MESSAGE 里为 5201/5301 准备的友好文案永不命中，统一改用 ErrorCode 枚举）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 业务错误码 + 自定义提示：错误码取自枚举，提示信息按场景定制
     * （如 PARAM_INVALID + "缺少 userId 参数"）。
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}

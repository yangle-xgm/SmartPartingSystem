package com.smartparking.common.core.handler;

import com.smartparking.common.core.domain.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器回归测试：路径不存在必须是 404，不能落进兜底变成 500。
 *
 * <p>背景：2026-11-06 联调时访问 `/api/audit`（真实路径是 `/api/audit/list`）返回
 * `{"code":500,"message":"系统内部错误"}`，日志里是 Spring 6.1 的
 * `NoResourceFoundException: No static resource api/audit.`——被 Runtime/Exception
 * 兜底接走了，调用方会误判成服务故障。</p>
 */
class BaseGlobalExceptionHandlerTest {

    private final BaseGlobalExceptionHandler handler = new BaseGlobalExceptionHandler() { };

    @Test
    @DisplayName("路径不存在：返回 404 + 接口路径，而不是 500")
    void noResourceFound_returns404() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "api/audit");

        Result<Void> result = handler.handleNoResourceFound(e);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).isEqualTo("接口不存在：api/audit");
    }
}

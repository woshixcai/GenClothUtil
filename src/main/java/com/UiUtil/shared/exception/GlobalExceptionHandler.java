package com.UiUtil.shared.exception;

import com.UiUtil.shared.result.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理：将异常统一包装为 ApiResult，同时打印接口路径 + 根因，方便快速定位。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public ApiResult<Void> handleRuntime(RuntimeException e, HttpServletRequest req) {
        Throwable root = rootCause(e);
        log.error("[{}] 业务异常 @ {}:{} — {}",
                req.getRequestURI(),
                root.getStackTrace().length > 0 ? root.getStackTrace()[0].getClassName() : "?",
                root.getStackTrace().length > 0 ? root.getStackTrace()[0].getLineNumber() : -1,
                root.getMessage(),
                e);
        return ApiResult.fail(e.getMessage() != null ? e.getMessage() : "服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e, HttpServletRequest req) {
        Throwable root = rootCause(e);
        log.error("[{}] 系统异常 @ {}:{} — {}",
                req.getRequestURI(),
                root.getStackTrace().length > 0 ? root.getStackTrace()[0].getClassName() : "?",
                root.getStackTrace().length > 0 ? root.getStackTrace()[0].getLineNumber() : -1,
                root.getMessage(),
                e);
        return ApiResult.fail("系统异常，请稍后重试");
    }

    /** 递归取最内层根因 */
    private static Throwable rootCause(Throwable t) {
        Throwable cause = t.getCause();
        return (cause == null || cause == t) ? t : rootCause(cause);
    }
}

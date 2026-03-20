package com.UiUtil.shared.result;

import lombok.Data;

/**
 * 通用接口返回体
 * 使用方式：ApiResult.ok(data) / ApiResult.fail("错误信息")
 */
@Data
public class ApiResult<T> {

    private int code;
    private String msg;
    private T data;

    private ApiResult(int code, String msg, T data) {
        this.code = code;
        this.msg  = msg;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "success", data);
    }

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(200, "success", null);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return new ApiResult<>(500, msg, null);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }

    /** 401 未认证 */
    public static <T> ApiResult<T> unauthorized() {
        return new ApiResult<>(401, "未登录或 Token 已过期", null);
    }

    /** 403 无权限 */
    public static <T> ApiResult<T> forbidden() {
        return new ApiResult<>(403, "权限不足", null);
    }
}

package com.yourcompany.common.base.response;

import lombok.Getter;
import lombok.Setter;

/**
 * HTTP 接口统一响应包装。
 *
 * <p>所有 Controller 方法必须返回本类型。成功使用 {@link #success(Object)}，
 * 失败由全局异常处理器统一转换，Controller 内不允许手动 return error 兜底。
 */
@Getter
@Setter
public class Result<T> {

    /**
     * 业务码，成功固定为 "SUCCESS"
     */
    private String code;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 业务数据，只能是 VO 或基础类型
     */
    private T data;

    public Result() {
    }

    public Result(Boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, ResponseCode.SUCCESS.name(), ResponseCode.SUCCESS.name(), data);
    }

    public static <T> Result<T> error(String errorCode, String errorMsg) {
        return new Result<>(false, errorCode, errorMsg, null);
    }
}

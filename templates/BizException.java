package com.yourcompany.common.base.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 *
 * <p>各模块应继承本类定义自己的 XxxException（如 OrderException），
 * 便于全局处理器按模块分类处理。
 *
 * <p>禁止裸抛 RuntimeException / IllegalArgumentException 表达业务失败。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " | " + detail);
        this.errorCode = errorCode;
    }

    public BizException(String code, String message) {
        super(message);
        this.errorCode = new SimpleErrorCode(code, message);
    }
}

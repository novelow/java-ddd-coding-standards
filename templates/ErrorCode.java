package com.yourcompany.common.base.exception;

/**
 * 错误码统一接口。
 *
 * <p>所有错误码枚举（通用、模块级）必须实现本接口，
 * 全局异常处理器才能统一提取 code 与 message。
 */
public interface ErrorCode {

    String getCode();

    String getMessage();
}

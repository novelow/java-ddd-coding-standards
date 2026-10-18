package com.yourcompany.common.base.response;

/**
 * 通用响应码。模块级业务错误码请在各自模块的 XxxErrorCode 中定义。
 */
public enum ResponseCode {

    SUCCESS,
    BIZ_ERROR,
    PARAM_ERROR,
    SYS_ERROR
}

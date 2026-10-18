package com.yourcompany.common.base.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用业务错误码。
 *
 * <p>模块级错误码请按同样格式新建 XxxErrorCode，命名格式：模块_具体错误，全大写下划线。
 * 禁止使用数字错误码。
 */
@Getter
@AllArgsConstructor
public enum BizErrorCode implements ErrorCode {

    PARAM_ERROR("PARAM_ERROR", "参数错误"),
    BIZ_ERROR("BIZ_ERROR", "业务处理失败"),
    SYS_ERROR("SYS_ERROR", "系统异常，请稍后重试"),
    DATA_NOT_EXIST("DATA_NOT_EXIST", "数据不存在"),
    STATE_TRANSITION_FAILED("STATE_TRANSITION_FAILED", "状态流转非法"),
    CONCURRENT_CONFLICT("CONCURRENT_CONFLICT", "并发冲突，请重试"),
    OPERATE_NOT_ALLOWED("OPERATE_NOT_ALLOWED", "当前状态不允许该操作"),
    DUPLICATED_REQUEST("DUPLICATED_REQUEST", "重复请求");

    private final String code;

    private final String message;
}
